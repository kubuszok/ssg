/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/serialize.dart (~1300 lines)
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: serialize.dart -> SerializeVisitor.scala
 *   Idiom: Minimum viable implementation. Supports expanded (default) and
 *     compressed output styles. Emits stylesheets, style rules, declarations,
 *     comments, at-rules, media rules, supports rules, imports, keyframe blocks.
 *   Source map generation: minimal v3 source map. When sourceMap=true, the
 *     visitor records (genLine, genCol, srcIdx, srcLine, srcCol) per emitted
 *     declaration and serializes them as a VLQ-encoded "mappings" string in a
 *     v3 JSON object: {"version":3,"sources":[...],"names":[],"mappings":"..."}.
 */
package ssg
package sass
package visitor

import ssg.sass.ColorNames
import ssg.sass.Nullable
import ssg.sass.ast.css.{ CssAtRule, CssComment, CssDeclaration, CssImport, CssKeyframeBlock, CssMediaQuery, CssMediaRule, CssNode, CssParentNode, CssStyleRule, CssStylesheet, CssSupportsRule }
import ssg.sass.ast.selector.{ ComplexSelector, SelectorList }
import ssg.sass.util.NumberUtil
import ssg.sass.value.{ ColorFormat, ListSeparator, SassColor, SassList, SassMap, SassNull, SassNumber, SassString, SpanColorFormat, Value }
import ssg.sass.value.color.{ ColorSpace, LinearChannel }

import scala.util.boundary
import scala.util.boundary.break

/** Output style for serialization: "expanded" (default, multi-line) or "compressed" (single-line, no whitespace).
  */
object OutputStyle {
  val Expanded:   String = "expanded"
  val Compressed: String = "compressed"
}

/** Result of serializing a CSS AST: the CSS text plus an optional source map. */
final case class SerializeResult(css: String, sourceMap: Nullable[String] = Nullable.empty[String])

/** A visitor that converts a CSS AST into CSS text. */
final class SerializeVisitor(
  val style:     String = OutputStyle.Expanded,
  val inspect:   Boolean = false,
  val sourceMap: Boolean = false
) extends CssVisitor[Unit] {

  private val buffer = new StringBuilder()
  private var indentLevel: Int = 0

  // ---- Source map state -----------------------------------------------------
  // 0-based generated cursor, recomputed lazily from `buffer` before each entry.
  private var genLine: Int = 0
  private var genCol:  Int = 0

  // Sources table (insertion order) and url -> index map.
  private val sourcesList = scala.collection.mutable.ArrayBuffer[String]()
  private val sourceIndex = scala.collection.mutable.LinkedHashMap[String, Int]()

  // Per-generated-line list of mapping segments. Each segment is
  // (genCol, srcIdx, srcLine, srcCol).
  private val segmentsByLine = scala.collection.mutable.ArrayBuffer[scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]]()
  segmentsByLine += scala.collection.mutable.ArrayBuffer.empty

  private def isCompressed: Boolean = style == OutputStyle.Compressed

  // ---------------------------------------------------------------------------
  // Invisibility check — matches dart-sass `_IsInvisibleVisitor` semantics in
  // the subset of the AST that ssg-sass currently populates. A parent node is
  // considered invisible if it isn't childless AND every child is invisible.
  // Declarations, imports, and preserved comments are always visible. Regular
  // comments are visible except in compressed mode.
  // ---------------------------------------------------------------------------
  private def isNodeInvisible(node: CssNode): Boolean = node match {
    case _: CssDeclaration => false
    case _: CssImport      => false
    case c: CssComment     => isCompressed && !c.isPreserved
    case rule: CssStyleRule =>
      // A style rule is invisible if its selector is invisible (all complex
      // selectors contain placeholders or are bogus) OR if all children are
      // invisible. Matches dart-sass _IsInvisibleVisitor.visitCssStyleRule.
      val selectorInvisible = rule.selector.isInvisible
      selectorInvisible || (
        !rule.isChildless && rule.children.forall(isNodeInvisible)
      )
    case at: CssAtRule =>
      // Generic at-rules with empty bodies ({}) are visible — they're CSS
      // passthrough. But at-rules with all-invisible children are invisible.
      if (at.isChildless) false
      else at.children.nonEmpty && at.children.forall(isNodeInvisible)
    case p: CssParentNode =>
      if (p.isChildless) false
      else p.children.forall(isNodeInvisible)
    case _ => false
  }

  /** Recomputes the (line, column) cursor from the current buffer length. */
  private def syncCursor(): Unit = {
    var line = 0
    var col  = 0
    var i    = 0
    val len  = buffer.length
    while (i < len) {
      if (buffer.charAt(i) == '\n') {
        line += 1
        col = 0
      } else {
        col += 1
      }
      i += 1
    }
    genLine = line
    genCol = col
    while (segmentsByLine.length <= line)
      segmentsByLine += scala.collection.mutable.ArrayBuffer.empty
  }

  /** Records a source-map entry at the current generated cursor for the given source span. */
  private def recordMapping(span: ssg.sass.util.FileSpan): Unit =
    if (sourceMap && span != null) {
      syncCursor()
      val url = span.file.url.getOrElse("stdin")
      val idx = sourceIndex.getOrElseUpdate(
        url, {
          val n = sourcesList.length
          sourcesList += url
          n
        }
      )
      segmentsByLine(genLine) += ((genCol, idx, span.start.line, span.start.column))
    }

  /** Serialize the given stylesheet to CSS text.
    *
    * If the output contains any non-ASCII code point, a charset prefix is
    * prepended: `@charset "UTF-8";\n` in expanded mode, or the UTF-8 BOM
    * (U+FEFF) in compressed mode. Mirrors dart-sass `serialize()` in
    * lib/src/visitor/serialize.dart. The prefix is NOT tracked in the
    * source map — dart-sass forwards a `prefix:` parameter to the
    * SourceMapBuffer's `buildSourceMap` so the first real segment
    * remains column-0; we approximate this by keeping the source-map
    * segment table untouched when the prefix is emitted as expanded,
    * since the prefix ends in a newline and the first real line is
    * still line 1 in the emitted output. For compressed mode the BOM
    * is a single char that does not move the column index in source
    * maps (browsers treat it as a file-level byte marker).
    */
  def serialize(node: CssStylesheet): SerializeResult = {
    buffer.clear()
    indentLevel = 0
    genLine = 0
    genCol = 0
    sourcesList.clear()
    sourceIndex.clear()
    segmentsByLine.clear()
    segmentsByLine += scala.collection.mutable.ArrayBuffer.empty
    visitCssStylesheet(node)
    val css = buffer.toString()
    val prefix: String =
      if (containsNonAscii(css)) {
        if (isCompressed) "\uFEFF" else "@charset \"UTF-8\";\n"
      } else ""
    val mapJson: Nullable[String] =
      if (sourceMap) Nullable(buildSourceMapJson()) else Nullable.empty[String]
    SerializeResult(prefix + css, sourceMap = mapJson)
  }

  /** Returns true if any code point in `s` is outside the ASCII range. */
  private def containsNonAscii(s: String): Boolean = {
    var i = 0
    while (i < s.length) {
      if (s.charAt(i) > 0x7F) return true
      i += 1
    }
    false
  }

  /** Builds a v3 source map JSON object from the recorded segments. */
  private def buildSourceMapJson(): String = {
    val sources  = sourcesList.toList
    val mappings = encodeMappings()
    val sb       = new StringBuilder()
    sb.append("{\"version\":3,\"sources\":[")
    var first = true
    for (s <- sources) {
      if (!first) sb.append(',')
      first = false
      sb.append('"')
      sb.append(jsonEscape(s))
      sb.append('"')
    }
    sb.append("],\"names\":[],\"mappings\":\"")
    sb.append(mappings)
    sb.append("\"}")
    sb.toString()
  }

  private def jsonEscape(s: String): String = {
    val sb = new StringBuilder()
    var i  = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case _    =>
          if (c < 0x20) sb.append("\\u%04x".format(c.toInt))
          else sb.append(c)
      }
      i += 1
    }
    sb.toString()
  }

  /** VLQ-encodes the recorded segments into a source map "mappings" string. */
  private def encodeMappings(): String = {
    val sb          = new StringBuilder()
    var prevSrcIdx  = 0
    var prevSrcLine = 0
    var prevSrcCol  = 0
    var lineIdx     = 0
    val totalLines  = segmentsByLine.length
    while (lineIdx < totalLines) {
      if (lineIdx > 0) sb.append(';')
      val segs       = segmentsByLine(lineIdx)
      var prevGenCol = 0
      var first      = true
      for ((gc, si, sl, sc) <- segs) {
        if (!first) sb.append(',')
        first = false
        sb.append(SerializeVisitor.vlqEncode(gc - prevGenCol))
        sb.append(SerializeVisitor.vlqEncode(si - prevSrcIdx))
        sb.append(SerializeVisitor.vlqEncode(sl - prevSrcLine))
        sb.append(SerializeVisitor.vlqEncode(sc - prevSrcCol))
        prevGenCol = gc
        prevSrcIdx = si
        prevSrcLine = sl
        prevSrcCol = sc
      }
      lineIdx += 1
    }
    sb.toString()
  }

  // ---------------------------------------------------------------------------
  // Formatting helpers
  // ---------------------------------------------------------------------------

  private def writeIndent(): Unit =
    if (!isCompressed) {
      var i = 0
      while (i < indentLevel) {
        buffer.append("  ")
        i += 1
      }
    }

  private def writeLine(): Unit =
    if (!isCompressed) buffer.append('\n')

  private def writeSpace(): Unit =
    if (!isCompressed) buffer.append(' ')

  // ---------------------------------------------------------------------------
  // Stage 1: sibling spacing & semicolon emission
  // Ported from dart-sass `_requiresSemicolon` / `_isTrailingComment` /
  // `_visitChildren` / `visitCssStylesheet` in lib/src/visitor/serialize.dart.
  // The blank-line-between-siblings rule is conditional: dart-sass emits
  // exactly one line feed by default, plus a second line feed when the
  // previous sibling has `isGroupEnd == true` (e.g. closes a media/style
  // rule). Trailing comments collapse onto the previous line via a space.
  // ---------------------------------------------------------------------------

  /** Whether [node] requires a semicolon to be written after it. */
  private def requiresSemicolon(node: CssNode): Boolean = node match {
    case p: CssParentNode => p.isChildless
    case _: CssComment    => false
    case _                => true
  }

  /** Whether [node] is a trailing comment that should be appended to [previous]'s line. */
  private def isTrailingComment(node: CssNode, previous: CssNode): Boolean = {
    if (isCompressed) return false
    node match {
      case _: CssComment =>
        val nodeSpan = node.span
        val prevSpan = previous.span
        if (nodeSpan == null || prevSpan == null) false
        else if (nodeSpan.file.url != prevSpan.file.url) false
        else if (!prevSpan.contains(nodeSpan)) {
          nodeSpan.start.line == prevSpan.end.line
        } else {
          // Heuristic: if the comment starts on the same line as the parent's
          // first line (i.e., before the `{`), treat as trailing.
          nodeSpan.start.line == prevSpan.start.line
        }
      case _ => false
    }
  }

  /** Emits a brace block of [children], filtering invisible nodes and applying dart-sass sibling spacing. */
  private def writeChildrenIn(parent: CssParentNode | Null, children: List[CssNode]): Unit = {
    buffer.append('{')
    var prePrevious: CssNode | Null = null
    var previous:    CssNode | Null = null
    for (child <- children) {
      if (!isNodeInvisible(child)) {
        if (previous != null && requiresSemicolon(previous)) buffer.append(';')
        val precedent: CssNode | Null = if (previous != null) previous else parent
        if (precedent != null && isTrailingComment(child, precedent)) {
          // Trailing comment: append on the same line with a single space.
          writeSpace()
          child.accept(this)
        } else {
          writeLine()
          // Note: dart-sass _visitChildren does NOT check isGroupEnd —
          // blank lines between groups only happen at the stylesheet level
          // (visitCssStylesheet), not inside brace blocks.
          indentLevel += 1
          writeIndent()
          child.accept(this)
          indentLevel -= 1
        }
        prePrevious = previous
        previous = child
      }
    }
    if (previous != null) {
      if (requiresSemicolon(previous) && !isCompressed) buffer.append(';')
      // Closing the block: write a line feed + outer indentation, unless
      // the only child was a same-line trailing comment relative to the parent.
      if (prePrevious == null && parent != null && isTrailingComment(previous, parent)) {
        writeSpace()
      } else {
        writeLine()
        writeIndent()
      }
    }
    buffer.append('}')
  }

  // ---------------------------------------------------------------------------
  // Visitor methods
  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // Value formatting (color shorthand, named colors, number tweaks)
  // ---------------------------------------------------------------------------

  /** Formats a value for emission in a declaration. Applies per-type customizations (color shorthand, named-color preference, compressed-mode number tweaks).
    */
  private def formatValue(v: Value): String = v match {
    case c: SassColor  => formatColorDispatch(c)
    case n: SassNumber => formatSassNumber(n)
    case s: SassString => formatString(s)
    case l: SassList   => formatList(l)
    case m: SassMap    => formatMap(m)
    // dart-sass renders `null` as `"null"` in inspect mode (used by
    // `meta.inspect()`) and as an empty string in normal output, since
    // null values are filtered from declaration-value serialization.
    case SassNull      => if (inspect) "null" else ""
    case _             => v.toCssString()
  }

  /** Public forwarder so the companion-object `serializeValue` entry
    * point can reach the private formatter without exposing it to
    * unrelated consumers.
    */
  private[visitor] def formatValuePublic(v: Value): String = formatValue(v)

  // ---------------------------------------------------------------------------
  // Stage 2: modern color space dispatch
  // Ported from dart-sass `visitColor` in lib/src/visitor/serialize.dart.
  // The dispatch is:
  //   - Legacy spaces (rgb/hsl/hwb) with no missing channels  → legacy handler
  //   - rgb with any missing channel                          → modern rgb()
  //   - hsl/hwb with any missing channel                      → modern hsl()/hwb()
  //   - lab/lch/oklab/oklch                                   → modern function syntax
  //   - everything else (xyz, display-p3, ...)                → color() function
  // Ported from dart-sass `visitColor` / `_writeLegacyColor` / `_writeHsl` /
  // `_writeHwb` / `_writeRgb` / `_writeColorFunction` / `_writeChannel` /
  // `_maybeWriteSlashAlpha` in lib/src/visitor/serialize.dart (lines 616-991).
  // ---------------------------------------------------------------------------

  private def commaSeparator: String = if (isCompressed) "," else ", "

  /** Main color dispatch — matches dart-sass `visitColor`. */
  private def formatColorDispatch(c: SassColor): String = {
    val space = c.space
    val noMissing = !c.isChannel0Missing && !c.isChannel1Missing &&
      !c.isChannel2Missing && !c.isAlphaMissing

    if (((space eq ColorSpace.rgb) || (space eq ColorSpace.hsl) || (space eq ColorSpace.hwb)) && noMissing) {
      writeLegacyColor(c)
    } else if (space eq ColorSpace.rgb) {
      // RGB with missing channels: modern rgb() with none
      val sb = new StringBuilder()
      sb.append("rgb(")
      writeChannel(sb, c.channel0OrNull)
      sb.append(' ')
      writeChannel(sb, c.channel1OrNull)
      sb.append(' ')
      writeChannel(sb, c.channel2OrNull)
      maybeWriteSlashAlpha(sb, c)
      sb.append(')')
      sb.toString()
    } else if ((space eq ColorSpace.hsl) || (space eq ColorSpace.hwb)) {
      // HSL/HWB with missing channels: modern function syntax with none
      val sb = new StringBuilder()
      sb.append(space.toString)
      sb.append('(')
      writeChannel(sb, c.channel0OrNull, if (isCompressed) Nullable.Null else Nullable("deg"))
      sb.append(' ')
      writeChannel(sb, c.channel1OrNull, Nullable("%"))
      sb.append(' ')
      writeChannel(sb, c.channel2OrNull, Nullable("%"))
      maybeWriteSlashAlpha(sb, c)
      sb.append(')')
      sb.toString()
    } else if ((space eq ColorSpace.lab) || (space eq ColorSpace.lch) ||
               (space eq ColorSpace.oklab) || (space eq ColorSpace.oklch)) {
      formatLabLchColor(c)
    } else {
      writeColorFunction(c)
    }
  }

  /** Dispatches lab/lch/oklab/oklch serialization: color-mix() for out-of-gamut,
    * function syntax for in-gamut. Matches dart-sass visitColor cases 4-7.
    */
  private def formatLabLchColor(c: SassColor): String = {
    val space = c.space
    val isOk = (space eq ColorSpace.oklab) || (space eq ColorSpace.oklch)
    val lightnessMax = if (isOk) 1.0 else 100.0
    val polar = space.channels(2).isPolarAngle

    // Out-of-gamut conditions that need color-mix() (only when no missing channels)
    val lightnessOutOfGamut = !inspect &&
      !NumberUtil.fuzzyInRange(c.channel0, 0, lightnessMax) &&
      !c.isChannel1Missing && !c.isChannel2Missing

    val negativeChroma = polar && !inspect &&
      NumberUtil.fuzzyLessThan(c.channel1, 0) &&
      !c.isChannel0Missing && !c.isChannel1Missing

    if (lightnessOutOfGamut || negativeChroma) {
      writeColorMix(c)
    } else {
      writeLabLchFunction(c)
    }
  }

  /** Writes a legacy color in the shortest compatible format.
    *
    * Unlike newer color spaces, the three legacy color spaces are interchangeable.
    * We choose the shortest representation compatible with all browsers.
    * Ported from dart-sass `_writeLegacyColor`.
    */
  private def writeLegacyColor(color: SassColor): String = boundary[String] {
    val opaque = NumberUtil.fuzzyEquals(color.alpha, 1)

    // Out-of-gamut colors can only be represented accurately as HSL because
    // only HSL isn't clamped at parse time. Skip when any channel is NaN since
    // HSL conversion produces meaningless results for NaN.
    if (!color.isInGamut && !inspect && !hasNaNChannel(color)) {
      break(writeHsl(color))
    }

    // In compressed mode, emit in the shortest representation possible.
    if (isCompressed) {
      val rgb = color.toSpace(ColorSpace.rgb)
      if (opaque) {
        val sb = new StringBuilder()
        if (tryIntegerRgb(sb, rgb)) {
          break(sb.toString())
        }
      }

      val red = numberToString(rgb.channel0)
      val green = numberToString(rgb.channel1)
      val blue = numberToString(rgb.channel2)

      val hsl = color.toSpace(ColorSpace.hsl)
      val hue = numberToString(hsl.channel0)
      val saturation = numberToString(hsl.channel1)
      val lightness = numberToString(hsl.channel2)

      val sb = new StringBuilder()
      // Add two characters for HSL for the %s on saturation and lightness.
      if (red.length + green.length + blue.length <=
          hue.length + saturation.length + lightness.length + 2) {
        sb.append(if (opaque) "rgb(" else "rgba(")
        sb.append(red)
        sb.append(',')
        sb.append(green)
        sb.append(',')
        sb.append(blue)
      } else {
        sb.append(if (opaque) "hsl(" else "hsla(")
        sb.append(hue)
        sb.append(',')
        sb.append(saturation)
        sb.append("%,")
        sb.append(lightness)
        sb.append('%')
      }
      if (!opaque) {
        sb.append(',')
        writeNumberTo(sb, color.alpha)
      }
      sb.append(')')
      break(sb.toString())
    }

    if (color.space eq ColorSpace.hsl) {
      break(writeHsl(color))
    }
    if (inspect && (color.space eq ColorSpace.hwb)) {
      break(writeHwb(color))
    }

    if (color.format.isDefined) {
      color.format.get match {
        case ColorFormat.RgbFunction => break(writeRgb(color))
        case span: SpanColorFormat  => break(span.original)
      }
    }

    // Always emit generated transparent colors in rgba format.
    // This works around an IE bug. See sass/sass#1782.
    if (opaque) {
      val rgb = color.toSpace(ColorSpace.rgb)
      // dart-sass serialize.dart:815-826: in expanded mode, always prefer
      // the named color if one exists. Hex is only used when no name matches.
      // This differs from compressed mode which picks the shortest form.
      val name = ColorNames.namesByColor.get(rgb)
      if (name.isDefined) break(name.get)

      if (canUseHex(rgb)) {
        val redInt = rgb.channel0.round.toInt
        val greenInt = rgb.channel1.round.toInt
        val blueInt = rgb.channel2.round.toInt
        val sb = new StringBuilder()
        sb.append('#')
        // dart-sass serialize.dart:820-825: expanded mode always uses
        // 6-digit hex. Short 3-digit form is only for compressed mode.
        writeHexComponent(sb, redInt)
        writeHexComponent(sb, greenInt)
        writeHexComponent(sb, blueInt)
        break(sb.toString())
      }
    }

    // If an HWB color can't be represented as hex, write as HSL since
    // that more clearly captures the author's intent.
    if (color.space eq ColorSpace.hwb) writeHsl(color) else writeRgb(color)
  }

  /** Writes color as `hsl(h, s%, l%)` or `hsla(h, s%, l%, a)`.
    * Ported from dart-sass `_writeHsl`.
    */
  private def writeHsl(color: SassColor): String = {
    val opaque = NumberUtil.fuzzyEquals(color.alpha, 1)
    val hsl = color.toSpace(ColorSpace.hsl)
    val sb = new StringBuilder()
    sb.append(if (opaque) "hsl(" else "hsla(")
    writeChannel(sb, Nullable(hsl.channel("hue")))
    sb.append(commaSeparator)
    writeChannel(sb, Nullable(hsl.channel("saturation")), Nullable("%"))
    sb.append(commaSeparator)
    writeChannel(sb, Nullable(hsl.channel("lightness")), Nullable("%"))
    if (!opaque) {
      sb.append(commaSeparator)
      writeNumberTo(sb, color.alpha)
    }
    sb.append(')')
    sb.toString()
  }

  /** Writes color as `hwb(h w% b%)`. Only used in inspect mode.
    * Ported from dart-sass `_writeHwb`.
    */
  private def writeHwb(color: SassColor): String = {
    val sb = new StringBuilder()
    sb.append("hwb(")
    val hwb = color.toSpace(ColorSpace.hwb)
    writeNumberTo(sb, hwb.channel("hue"))
    sb.append(' ')
    writeNumberTo(sb, hwb.channel("whiteness"))
    sb.append('%')
    sb.append(' ')
    writeNumberTo(sb, hwb.channel("blackness"))
    sb.append('%')
    if (!NumberUtil.fuzzyEquals(color.alpha, 1)) {
      sb.append(" / ")
      writeNumberTo(sb, color.alpha)
    }
    sb.append(')')
    sb.toString()
  }

  /** Writes color as `rgb(r, g, b)` or `rgba(r, g, b, a)`.
    * Ported from dart-sass `_writeRgb`.
    */
  private def writeRgb(color: SassColor): String = {
    val opaque = NumberUtil.fuzzyEquals(color.alpha, 1)
    val rgb = color.toSpace(ColorSpace.rgb)
    val sb = new StringBuilder()
    sb.append(if (opaque) "rgb(" else "rgba(")
    writeChannel(sb, Nullable(rgb.channel("red")))
    sb.append(commaSeparator)
    writeChannel(sb, Nullable(rgb.channel("green")))
    sb.append(commaSeparator)
    writeChannel(sb, Nullable(rgb.channel("blue")))
    if (!opaque) {
      sb.append(commaSeparator)
      writeChannel(sb, Nullable(color.alpha))
    }
    sb.append(')')
    sb.toString()
  }

  /** Writes lab/lch/oklab/oklch function syntax with relative color for edge cases.
    * Ported from dart-sass `visitColor` case 7.
    */
  private def writeLabLchFunction(c: SassColor): String = {
    val sb = new StringBuilder()
    sb.append(c.space.toString)
    sb.append('(')

    val polar = c.space.channels(2).isPolarAngle

    // Relative color syntax for out-of-bounds with missing channels
    // (color-mix can't represent `none`, so we fall back to relative syntax)
    if (!inspect &&
        (!NumberUtil.fuzzyInRange(c.channel0, 0, 100) ||
          (polar && NumberUtil.fuzzyLessThan(c.channel1, 0)))) {
      sb.append("from ")
      sb.append(if (isCompressed) "red" else "black")
      sb.append(' ')
    }

    // Lightness: write as percentage when not compressed and not missing
    if (!isCompressed && !c.isChannel0Missing) {
      val max = c.space.channels(0).asInstanceOf[LinearChannel].max
      writeNumberTo(sb, c.channel0 * 100 / max)
      sb.append('%')
    } else {
      writeChannel(sb, c.channel0OrNull)
    }
    sb.append(' ')
    writeChannel(sb, c.channel1OrNull)
    sb.append(' ')
    writeChannel(sb, c.channel2OrNull, if (polar && !isCompressed) Nullable("deg") else Nullable.Null)
    maybeWriteSlashAlpha(sb, c)
    sb.append(')')
    sb.toString()
  }

  /** Writes `color(space c1 c2 c3 / alpha)` for non-legacy, non-lab/lch spaces.
    * Ported from dart-sass `_writeColorFunction`.
    */
  private def writeColorFunction(color: SassColor): String = {
    val sb = new StringBuilder()
    sb.append("color(")
    sb.append(color.space.toString)
    sb.append(' ')
    val chs = color.channelsOrNull
    writeChannel(sb, chs(0))
    sb.append(' ')
    writeChannel(sb, chs(1))
    sb.append(' ')
    writeChannel(sb, chs(2))
    maybeWriteSlashAlpha(sb, color)
    sb.append(')')
    sb.toString()
  }

  /** Writes `color-mix(in space, color(xyz-d65 ...) 100%, black)` for out-of-gamut
    * lab/lch/oklab/oklch colors. Ported from dart-sass visitColor cases 4-6.
    */
  private def writeColorMix(c: SassColor): String = {
    val sb = new StringBuilder()
    sb.append("color-mix(in ")
    sb.append(c.space.toString)
    sb.append(commaSeparator)
    // The XYZ space has no gamut restrictions, so we use it to represent
    // the out-of-gamut color before converting into the target space.
    sb.append(writeColorFunction(c.toSpace(ColorSpace.xyzD65)))
    writeOptionalSpace(sb)
    sb.append("100%")
    sb.append(commaSeparator)
    sb.append(if (isCompressed) "red" else "black")
    sb.append(')')
    sb.toString()
  }

  /** Writes a channel value, or `none` for missing. Ported from dart-sass `_writeChannel`.
    * Per CSS spec, NaN color channel values are treated as 0.
    */
  private def writeChannel(sb: StringBuilder, ch: Nullable[Double], unit: Nullable[String] = Nullable.Null): Unit = {
    if (ch.isEmpty) {
      sb.append("none")
    } else {
      val v = ch.get
      if (v.isNaN) {
        // CSS spec: NaN color channel values resolve to 0.
        sb.append('0')
        unit.foreach(u => sb.append(u))
      } else if (v.isFinite) {
        writeNumberTo(sb, v)
        unit.foreach(u => sb.append(u))
      } else {
        // Infinity: format via SassNumber path
        val num = if (unit.isDefined) SassNumber(v, unit.get) else SassNumber(v)
        sb.append(formatSassNumber(num))
      }
    }
  }

  /** Writes `/ alpha` if alpha is not 1. Ported from dart-sass `_maybeWriteSlashAlpha`. */
  private def maybeWriteSlashAlpha(sb: StringBuilder, color: SassColor): Unit = {
    if (!NumberUtil.fuzzyEquals(color.alpha, 1)) {
      writeOptionalSpace(sb)
      sb.append('/')
      writeOptionalSpace(sb)
      writeChannel(sb, color.alphaOrNull)
    }
  }

  /** Appends a space to sb if not in compressed mode. */
  private def writeOptionalSpace(sb: StringBuilder): Unit =
    if (!isCompressed) sb.append(' ')

  /** Renders a number as a string using the serializer's formatting rules. */
  private def numberToString(d: Double): String = {
    val sb = new StringBuilder()
    writeNumberTo(sb, d)
    sb.toString()
  }

  // --- Hex utilities ---

  /** Whether [rgb] can be represented as a hexadecimal color. */
  private def canUseHex(rgb: SassColor): Boolean =
    canUseHexForChannel(rgb.channel0) &&
    canUseHexForChannel(rgb.channel1) &&
    canUseHexForChannel(rgb.channel2)

  /** Whether a channel's value can be represented as a two-character hex value. */
  private def canUseHexForChannel(channel: Double): Boolean =
    NumberUtil.fuzzyIsInt(channel) &&
    NumberUtil.fuzzyGreaterThanOrEquals(channel, 0) &&
    NumberUtil.fuzzyLessThan(channel, 256)

  /** If value can be written as a hex code or color name, writes the shortest
    * form to sb and returns true. Otherwise writes nothing and returns false.
    */
  private def tryIntegerRgb(sb: StringBuilder, rgb: SassColor): Boolean = {
    if (!canUseHex(rgb)) false
    else {
      val redInt = rgb.channel0.round.toInt
      val greenInt = rgb.channel1.round.toInt
      val blueInt = rgb.channel2.round.toInt
      val shortHex = canUseShortHex(redInt, greenInt, blueInt)
      ColorNames.namesByColor.get(rgb) match {
        case Some(name) if name.length <= (if (shortHex) 4 else 7) =>
          sb.append(name)
        case _ =>
          if (shortHex) {
            sb.append('#')
            sb.append(hexCharFor(redInt & 0xF))
            sb.append(hexCharFor(greenInt & 0xF))
            sb.append(hexCharFor(blueInt & 0xF))
          } else {
            sb.append('#')
            writeHexComponent(sb, redInt)
            writeHexComponent(sb, greenInt)
            writeHexComponent(sb, blueInt)
          }
      }
      true
    }
  }

  /** Whether color can be represented as a short hex (e.g. `#fff`). */
  private def canUseShortHex(red: Int, green: Int, blue: Int): Boolean =
    isSymmetricalHex(red) && isSymmetricalHex(green) && isSymmetricalHex(blue)

  /** Whether a hex pair is symmetrical (e.g. `FF`). */
  private def isSymmetricalHex(color: Int): Boolean = (color & 0xF) == (color >> 4)

  /** Emits a color component as a two-character hex pair. */
  private def writeHexComponent(sb: StringBuilder, color: Int): Unit = {
    sb.append(hexCharFor(color >> 4))
    sb.append(hexCharFor(color & 0xF))
  }

  /** Converts 0-15 to a hex character. */
  private def hexCharFor(number: Int): Char =
    if (number < 10) ('0' + number).toChar else ('a' - 10 + number).toChar

  /** Whether any color channel (or alpha) contains NaN. */
  private def hasNaNChannel(c: SassColor): Boolean =
    c.channel0.isNaN || c.channel1.isNaN || c.channel2.isNaN || c.alpha.isNaN

  // ---------------------------------------------------------------------------
  // Stage A.2: quoted/unquoted string formatting
  // Ported from dart-sass `_visitQuotedString` / `_visitUnquotedString` in
  // lib/src/visitor/serialize.dart. Unquoted strings emit raw text (newlines
  // replaced with spaces to keep the declaration on one line). Quoted strings
  // prefer `"` unless the text contains `"` and no `'`, in which case `'` is
  // used; backslash and the active quote are escaped, and control chars use
  // `\hh ` hex form.
  // ---------------------------------------------------------------------------
  private def formatString(s: SassString): String =
    if (!s.hasQuotes) {
      visitUnquotedString(s.text)
    } else {
      visitQuotedString(s.text)
    }

  /// Writes an unquoted string with [string] contents to a new StringBuilder.
  ///
  /// Port of dart-sass `_visitUnquotedString` (serialize.dart:1452-1473).
  /// Folds newlines to a single space, collapses post-newline whitespace to a
  /// single space, and hex-escapes PUA characters in expanded mode.
  private def visitUnquotedString(string: String): String = {
    val sb = new StringBuilder()
    var afterNewline = false
    var i = 0
    while (i < string.length) {
      val c = string.charAt(i)
      if (c == '\n') {
        sb.append(' ')
        afterNewline = true
        i += 1
      } else if (c == ' ' && afterNewline) {
        // Collapse post-newline whitespace: skip spaces after a newline.
        i += 1
      } else {
        afterNewline = false
        tryPrivateUseCharacter(sb, c.toInt, string, i) match {
          case Some(newIndex) =>
            i = newIndex + 1
          case scala.None =>
            sb.append(c)
            i += 1
        }
      }
    }
    sb.toString()
  }

  /// Writes a quoted string to a new StringBuilder.
  ///
  /// Port of dart-sass `_visitQuotedString` (serialize.dart:1348-1448).
  /// Handles PUA character escaping in expanded mode.
  private def visitQuotedString(text: String): String = {
    var hasDouble = false
    var hasSingle = false
    var i         = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '"') hasDouble = true
      else if (c == '\'') hasSingle = true
      i += 1
    }
    val q  = if (hasDouble && !hasSingle) '\'' else '"'
    val sb = new StringBuilder()
    sb.append(q)
    i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      c match {
        case '\\'                       => sb.append("\\\\")
        case _ if c == q                => sb.append('\\'); sb.append(c)
        case _ if c < 0x20 || c == 0x7f =>
          // dart-sass serialize.dart:1519-1528 (_writeEscape): only add
          // a trailing space when the NEXT character is a hex digit,
          // space, or tab (CSS hex escape terminator rule).
          writeEscape(sb, c.toInt, text, i)
        case _ =>
          tryPrivateUseCharacter(sb, c.toInt, text, i) match {
            case Some(newIndex) =>
              i = newIndex
            case scala.None =>
              sb.append(c)
          }
      }
      i += 1
    }
    sb.append(q)
    sb.toString()
  }

  /// If [codeUnit] is (the beginning of) a private-use character and Sass isn't
  /// emitting compressed CSS, writes that character as an escape to [sb].
  ///
  /// Returns Some(lastConsumedIndex) on success, None otherwise.
  ///
  /// In expanded mode, we print all characters in Private Use Areas as escape
  /// codes since there's no useful way to render them directly. These
  /// characters are often used for glyph fonts, where it's useful for readers
  /// to be able to distinguish between them in the rendered stylesheet.
  ///
  /// Port of dart-sass `_tryPrivateUseCharacter` (serialize.dart:1475-1511).
  private def tryPrivateUseCharacter(
    sb:       StringBuilder,
    codeUnit: Int,
    string:   String,
    i:        Int
  ): Option[Int] = {
    if (isCompressed) return scala.None

    // BMP Private Use Area: U+E000-U+F8FF
    if (codeUnit >= 0xE000 && codeUnit <= 0xF8FF) {
      writeEscape(sb, codeUnit, string, i)
      return Some(i)
    }

    // High surrogate for Supplementary Private Use Areas:
    // U+DB80-U+DBFF (high surrogates for plane 15-16 PUA)
    if (codeUnit >= 0xDB80 && codeUnit <= 0xDBFF && string.length > i + 1) {
      val low = string.charAt(i + 1).toInt
      val combined = ((codeUnit - 0xD800) << 10) + (low - 0xDC00) + 0x10000
      writeEscape(sb, combined, string, i + 1)
      return Some(i + 1)
    }

    scala.None
  }

  /// Writes [character] as a hexadecimal escape sequence to [sb].
  ///
  /// Port of dart-sass `_writeEscape` (serialize.dart:1519-1528).
  private def writeEscape(sb: StringBuilder, character: Int, string: String, i: Int): Unit = {
    sb.append('\\')
    sb.append(Integer.toHexString(character))

    if (i + 1 < string.length) {
      val next = string.charAt(i + 1)
      if (isHexChar(next) || next == ' ' || next == '\t') {
        sb.append(' ')
      }
    }
  }

  /// Returns whether [c] is a hexadecimal digit.
  private def isHexChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  // ---------------------------------------------------------------------------
  // Stage A.3: list and map formatting
  // Ported from dart-sass `_writeList` / `visitMap` in
  // lib/src/visitor/serialize.dart. Comma-separated lists use `, ` expanded /
  // `,` compressed; space-separated use a single space; slash-separated use
  // ` / ` expanded / `/` compressed. Blank elements are dropped for
  // non-comma separators (matching `_elementNeedsParens`/`isBlank` filter).
  // A single-element comma list is rendered as `(x,)`. Bracketed lists wrap
  // in `[...]`. Maps render as `(k1: v1, k2: v2)`.
  // ---------------------------------------------------------------------------
  private def formatList(l: SassList): String = {
    // Port of dart-sass `_writeList` in lib/src/visitor/serialize.dart.
    //
    // Handles:
    //   - `[]` for an empty bracketed list
    //   - `()` for an empty unbracketed list in inspect mode (throws
    //     outside inspect because an empty list isn't a valid CSS value)
    //   - `(x,)` for a single-element comma list in inspect mode
    //   - `(x/)` for a single-element slash list in inspect mode
    //   - `[x,]` for a single-element bracketed comma list
    //   - parentheses around nested sub-lists whose separator would be
    //     ambiguous with the outer separator (see elementNeedsParens)
    if (l.hasBrackets && l.asList.isEmpty) return "[]"
    if (!l.hasBrackets && l.asList.isEmpty) {
      if (inspect) return "()"
      else return "" // non-inspect: caller decides (property path emits blank)
    }

    val singleton =
      inspect && l.asList.length == 1 &&
        (l.separator == ListSeparator.Comma || l.separator == ListSeparator.Slash)

    val sb = new StringBuilder()
    if (singleton && !l.hasBrackets) sb.append('(')
    if (l.hasBrackets) sb.append('[')

    // dart-sass serialize.dart:1031-1033: in inspect mode keep all elements;
    // in CSS output mode drop blank elements (null, empty unbracketed lists)
    // regardless of separator or brackets.
    val elems =
      if (inspect) l.asList
      else l.asList.filterNot(_.isBlank)

    val sepStr = l.separator match {
      case ListSeparator.Comma     => if (isCompressed) "," else ", "
      case ListSeparator.Space     => " "
      case ListSeparator.Slash     => if (isCompressed) "/" else " / "
      case ListSeparator.Undecided => ""
    }

    var first = true
    for (elem <- elems) {
      if (first) first = false
      else sb.append(sepStr)
      // dart-sass only wraps nested-list elements in parentheses during
      // inspect mode; CSS-output mode flattens them because the slash/
      // space/comma separators are visually unambiguous in CSS even
      // when nested.
      if (inspect && elementNeedsParens(l.separator, elem)) {
        sb.append('(')
        sb.append(formatValue(elem))
        sb.append(')')
      } else {
        sb.append(formatValue(elem))
      }
    }

    if (singleton) {
      if (l.separator == ListSeparator.Comma) sb.append(',')
      else sb.append('/')
    }

    if (l.hasBrackets) sb.append(']')
    if (singleton && !l.hasBrackets) sb.append(')')

    sb.toString()
  }

  /** Whether a nested list element needs to be wrapped in parentheses to
    * disambiguate it from the outer list's separator. Port of dart-sass
    * `_elementNeedsParens` in serialize.dart.
    */
  private def elementNeedsParens(separator: ListSeparator, value: Value): Boolean = value match {
    case l: SassList =>
      if (l.asList.length < 2) false
      else if (l.hasBrackets) false
      else
        separator match {
          case ListSeparator.Comma =>
            l.separator == ListSeparator.Comma
          case ListSeparator.Slash =>
            l.separator == ListSeparator.Comma || l.separator == ListSeparator.Slash
          case _ =>
            l.separator != ListSeparator.Undecided
        }
    case _ => false
  }

  private def formatMap(m: SassMap): String = {
    val sep       = if (isCompressed) "," else ", "
    val kvSpacing = if (isCompressed) "" else " "
    // In inspect mode, wrap key/value sub-lists whose separator would
    // be ambiguous with the outer comma-separated map layout (e.g.
    // `((1, 2): 3)` vs `(1, 2: 3)`). Port of dart-sass's
    // `_visitMap` wrapping rule.
    def formatEntryPart(v: Value): String = v match {
      case l: SassList if inspect && l.asList.length >= 2 && !l.hasBrackets &&
            (l.separator == ListSeparator.Comma || l.separator == ListSeparator.Slash) =>
        s"(${formatValue(l)})"
      case _ => formatValue(v)
    }
    val entries = m.contents.map { case (k, v) =>
      s"${formatEntryPart(k)}:$kvSpacing${formatEntryPart(v)}"
    }.mkString(sep)
    s"($entries)"
  }

  /** Formats a SassNumber for CSS output.
    *
    * Ported from dart-sass `_SerializeVisitor.visitNumber` (serialize.dart):
    * the numeric portion is written via [[writeNumberTo]] (the faithful port
    * of `_writeNumber`), then the single numerator unit — if any — is
    * appended. Non-finite values (Infinity/-Infinity/NaN) wrap into
    * `calc(infinity * 1<unit>)` / `calc(-infinity * 1<unit>)` / `calc(NaN * 1<unit>)`
    * matching dart-sass `visitCalculation` + `_writeCalculationValue` +
    * `_writeCalculationUnits`. Complex units (multi-numerator or any
    * denominator) use the same calc wrapping so the output is a valid
    * first-class CSS calc() expression.
    */
  private def formatSassNumber(n: SassNumber): String = {
    // dart-sass serialize.dart:1108-1112 — slash-separated numbers emit
    // `before/after` recursively rather than the computed numeric value.
    if (n.asSlash.isDefined) {
      val (before, after) = n.asSlash.get
      return s"${formatSassNumber(before)}/${formatSassNumber(after)}"
    }
    if (!n.value.isFinite) return formatNonFiniteNumber(n)
    if (n.hasComplexUnits) return formatComplexUnitNumber(n)
    val sb = new StringBuilder()
    writeNumberTo(sb, n.value)
    if (n.numeratorUnits.nonEmpty) sb.append(n.numeratorUnits.head)
    sb.toString()
  }

  /** Wraps a non-finite SassNumber (Infinity / -Infinity / NaN) in a CSS
    * `calc(...)` expression, mirroring dart-sass
    * `_writeCalculationValue` for the `SassNumber(value: double(isFinite:
    * false))` branch.
    *
    * Examples (expanded mode):
    *   Infinity unitless   -> `calc(infinity)`
    *   -Infinity unitless  -> `calc(-infinity)`
    *   NaN unitless        -> `calc(NaN)`
    *   Infinity px         -> `calc(infinity * 1px)`
    *   NaN em / s          -> `calc(NaN * 1em / 1s)`
    */
  private def formatNonFiniteNumber(n: SassNumber): String = {
    val sb = new StringBuilder()
    sb.append("calc(")
    val value = n.value
    if (value == Double.PositiveInfinity) sb.append("infinity")
    else if (value == Double.NegativeInfinity) sb.append("-infinity")
    else sb.append("NaN")
    appendCalculationUnits(sb, n.numeratorUnits, n.denominatorUnits)
    sb.append(')')
    sb.toString()
  }

  /** Wraps a finite SassNumber with complex units (multi-numerator or any
    * denominator) in a `calc(...)` expression. Mirrors dart-sass
    * `visitCalculation` + `_writeCalculationValue` for the
    * `SassNumber(hasComplexUnits: true)` branch.
    */
  private def formatComplexUnitNumber(n: SassNumber): String = {
    val sb = new StringBuilder()
    sb.append("calc(")
    writeNumberTo(sb, n.value)
    n.numeratorUnits match {
      case first :: rest =>
        sb.append(first)
        appendCalculationUnits(sb, rest, n.denominatorUnits)
      case Nil =>
        appendCalculationUnits(sb, Nil, n.denominatorUnits)
    }
    sb.append(')')
    sb.toString()
  }

  /** Appends numerator / denominator units as ` * 1<unit>` / ` / 1<unit>`
    * factors inside a `calc(...)` expression. Port of dart-sass
    * `_writeCalculationUnits`.
    *
    * In compressed mode the spaces around `*` / `/` are dropped unless the
    * expression would become ambiguous (dart-sass only drops the space
    * around `*` for compression; `/` is always surrounded to avoid the
    * plain CSS division parse).
    */
  private def appendCalculationUnits(
    sb:               StringBuilder,
    numeratorUnits:   List[String],
    denominatorUnits: List[String]
  ): Unit = {
    val space = if (isCompressed) "" else " "
    for (unit <- numeratorUnits) {
      sb.append(space).append('*').append(space).append('1').append(unit)
    }
    for (unit <- denominatorUnits) {
      sb.append(space).append('/').append(space).append('1').append(unit)
    }
  }

  /** Writes `number` to `sb` without exponent notation and with at most `SassNumber.precision` digits after the decimal point.
    *
    * Ported from dart-sass `_writeNumber` / `_removeExponent` / `_writeRounded` in lib/src/visitor/serialize.dart. In compressed mode, strips the leading `0` from values like `0.5` -> `.5` (and
    * `-0.5` -> `-.5`). Emits integers without a trailing `.0`. Suppresses the minus sign when a negative value rounds to exactly zero.
    */
  private[visitor] def writeNumberTo(sb: StringBuilder, number: Double): Unit = {
    // Clamp doubles that are fuzzy-equal to an integer to their integer value.
    // In inspect mode only clamp on exact equality so full precision is shown.
    //
    // NumberUtil.fuzzyAsInt returns `Nullable[Int]` which silently truncates
    // values outside the 32-bit range. For emitted CSS we want the full Long
    // precision so large values like `math.$pi * 1e15` survive intact. We do
    // the fuzzy-integer test directly against Long here so we pick up values
    // up to ±9.2e18.
    if (number.isFinite) {
      val rounded = math.round(number)
      if (NumberUtil.fuzzyEquals(number, rounded.toDouble) && (!inspect || number == rounded.toDouble)) {
        sb.append(SerializeVisitor.removeExponent(rounded.toString))
        return
      }
    }

    var text = SerializeVisitor.removeExponent(SerializeVisitor.doubleToString(number))

    if (inspect) {
      sb.append(text)
      return
    }

    // Any double that's less than `SassNumber.precision + 2` characters long
    // is guaranteed to be safe to emit directly, since it'll contain at most
    // `0.` followed by `precision` digits.
    val canWriteDirectly = text.length < SassNumber.precision + 2
    if (canWriteDirectly) {
      if (isCompressed && text.charAt(0) == '0') text = text.substring(1)
      sb.append(text)
      return
    }

    writeRounded(sb, text)
  }

  /** Rounds `text` (a number written without exponent notation) to [[SassNumber.precision]] digits after the decimal point and writes the result to `sb`. Direct port of dart-sass `_writeRounded`.
    */
  private def writeRounded(sb: StringBuilder, text: String): Unit = {
    // Dart serializes doubles with a trailing `.0` for integer values; since
    // our `doubleToString` strips that, guard here anyway.
    if (text.endsWith(".0")) {
      sb.append(text, 0, text.length - 2)
      return
    }

    val digits      = new Array[Int](text.length + 1)
    var digitsIndex = 1

    var textIndex = 0
    val negative  = text.charAt(0) == '-'
    if (negative) textIndex += 1

    // Write the digits before the decimal to `digits`. If there's no decimal,
    // the number needs no rounding and can be written as-is.
    var sawDot = false
    while (!sawDot && textIndex < text.length) {
      val c = text.charAt(textIndex)
      textIndex += 1
      if (c == '.') sawDot = true
      else {
        digits(digitsIndex) = c - '0'
        digitsIndex += 1
      }
    }
    if (!sawDot) {
      sb.append(text)
      return
    }
    val firstFractionalDigit = digitsIndex

    val indexAfterPrecision = textIndex + SassNumber.precision
    if (indexAfterPrecision >= text.length) {
      sb.append(text)
      return
    }

    while (textIndex < indexAfterPrecision) {
      digits(digitsIndex) = text.charAt(textIndex) - '0'
      digitsIndex += 1
      textIndex += 1
    }

    // Round up if needed.
    if (text.charAt(textIndex) - '0' >= 5) {
      boundary {
        while (true) {
          digits(digitsIndex - 1) += 1
          if (digits(digitsIndex - 1) != 10) break(())
          else digitsIndex -= 1
        }
      }
    }

    // Zero any carried-over digits past the decimal.
    var i = digitsIndex
    while (i < firstFractionalDigit) {
      digits(i) = 0
      i += 1
    }
    while (digitsIndex > firstFractionalDigit && digits(digitsIndex - 1) == 0)
      digitsIndex -= 1

    // If rounded to exactly zero, emit a single `0` (no minus sign).
    if (digitsIndex == 2 && digits(0) == 0 && digits(1) == 0) {
      sb.append('0')
      return
    }

    if (negative) sb.append('-')

    // Write the digits before the decimal. Omit the leading `0` padding digit
    // added for rounding headroom; in compressed mode also omit the `0`
    // before the decimal point.
    var writtenIndex = 0
    if (digits(0) == 0) {
      writtenIndex += 1
      if (isCompressed && digits(1) == 0) writtenIndex += 1
    }
    while (writtenIndex < firstFractionalDigit) {
      sb.append(('0' + digits(writtenIndex)).toChar)
      writtenIndex += 1
    }

    if (digitsIndex > firstFractionalDigit) {
      sb.append('.')
      while (writtenIndex < digitsIndex) {
        sb.append(('0' + digits(writtenIndex)).toChar)
        writtenIndex += 1
      }
    }
  }

  override def visitCssStylesheet(node: CssStylesheet): Unit = {
    // dart-sass serialize.dart:173-194 (visitCssStylesheet): between visible
    // siblings emit a line feed (or trailing-comment space), and an extra
    // line feed when the previous sibling has `isGroupEnd == true`.
    var previous: CssNode | Null = null
    for (child <- node.children) {
      if (!isNodeInvisible(child)) {
        if (previous != null) {
          if (requiresSemicolon(previous)) buffer.append(';')
          if (isTrailingComment(child, previous)) {
            writeSpace()
          } else {
            writeLine()
            // dart-sass serialize.dart:183: extra blank line when the
            // previous node is a group end.
            //
            // Supplement: the evaluator does not yet propagate isGroupEnd
            // for media/supports blocks emitted directly at the top level
            // (not from flattened style rules). As a workaround, also add
            // a blank line when the previous sibling is a CssMediaRule or
            // CssSupportsRule with children. This matches the sass-spec
            // expected output.
            val prevNeedsBlankLine = previous.isGroupEnd || (previous match {
              case _: CssMediaRule    => true
              case _: CssSupportsRule => true
              case _                  => false
            })
            if (prevNeedsBlankLine) writeLine()
          }
        }
        previous = child
        child.accept(this)
      }
    }
    if (previous != null) {
      if (requiresSemicolon(previous) && !isCompressed) buffer.append(';')
      if (!isCompressed) buffer.append('\n')
    }
  }

  override def visitCssStyleRule(node: CssStyleRule): Unit = {
    recordMapping(node.span)
    buffer.append(formatSelectorList(node.selector))
    writeSpace()
    writeChildrenIn(node, node.children)
  }

  // ---------------------------------------------------------------------------
  // Stage A.4: selector list formatting
  // Ported from dart-sass `_visitComplexSelector` / `visitSelectorList` /
  // `visitCompoundSelector` in lib/src/visitor/serialize.dart. In expanded
  // mode, complex selectors are joined with `,\n<indent>`, components with
  // the combinator surrounded by spaces, and the descendant combinator is a
  // single space. In compressed mode, only the descendant combinator retains
  // its single space; `>`, `+`, and `~` have no surrounding whitespace, and
  // complex selectors are joined with a bare `,`.
  // ---------------------------------------------------------------------------
  private def formatSelectorList(list: SelectorList): String = {
    val sb    = new StringBuilder()
    var first = true
    // Filter out invisible complex selectors (placeholders, bogus combinators).
    // Matches dart-sass visitSelectorList which filters with `!complex.isInvisible`.
    val visible = list.components.filterNot(_.isInvisible)
    if (visible.isEmpty) return ""
    for (complex <- visible) {
      if (!first) {
        sb.append(',')
        if (!isCompressed) {
          // dart-sass `visitSelectorList`: if the complex selector is marked
          // with `lineBreak`, break the line and re-indent; otherwise emit a
          // single separating space (`_writeOptionalSpace`). The current
          // evaluator pipeline doesn't propagate `lineBreak` yet, so in
          // practice we take the space branch — matching the authored
          // selector in the common case.
          if (complex.lineBreak) {
            sb.append('\n')
            var i = 0
            while (i < indentLevel) {
              sb.append("  ")
              i += 1
            }
          } else {
            sb.append(' ')
          }
        }
      }
      first = false
      writeComplexSelectorTo(sb, complex)
    }
    sb.toString()
  }

  private def writeComplexSelectorTo(sb: StringBuilder, complex: ComplexSelector): Unit = {
    // Leading combinators (rare; bogus-but-parsed selectors like `> .a`).
    var leadingFirst = true
    for (c <- complex.leadingCombinators) {
      if (!leadingFirst && !isCompressed) sb.append(' ')
      leadingFirst = false
      sb.append(c.value.text)
    }
    if (complex.leadingCombinators.nonEmpty && complex.components.nonEmpty && !isCompressed) {
      sb.append(' ')
    }

    var compIdx = 0
    val comps   = complex.components
    while (compIdx < comps.length) {
      val component = comps(compIdx)
      sb.append(component.selector.toString)
      for (comb <- component.combinators)
        if (isCompressed) sb.append(comb.value.text)
        else {
          sb.append(' ')
          sb.append(comb.value.text)
        }
      if (compIdx < comps.length - 1) {
        // Descendant combinator (implicit) or space after an explicit
        // combinator emitted above. In expanded mode we always want a
        // separating space; in compressed mode only when the previous
        // component had no explicit combinator (descendant).
        if (isCompressed) {
          if (component.combinators.isEmpty) sb.append(' ')
        } else {
          sb.append(' ')
        }
      }
      compIdx += 1
    }
  }

  override def visitCssDeclaration(node: CssDeclaration): Unit = {
    // Record one mapping for the property name and a second for the value
    // so debuggers can highlight either side of the `name: value;` pair.
    // dart-sass: visitCssDeclaration does NOT emit a trailing `;`. The
    // separator is emitted by `_visitChildren` via `requiresSemicolon`
    // before the next sibling, or after the final child if non-compressed.
    recordMapping(node.span)
    buffer.append(node.name.value)
    buffer.append(':')
    // Stage 3: custom property formatting.
    // dart-sass `visitCssDeclaration`: when `parsedAsSassScript == false`
    // (a CSS custom property whose value was preserved as raw text), the
    // value is emitted via `_writeFoldedValue` (compressed) or
    // `_writeReindentedValue` (expanded) — NOT via the SassScript value
    // formatter. Custom properties have no leading space; their raw text
    // already includes the right whitespace.
    if (!node.parsedAsSassScript) {
      // dart-sass `visitCssDeclaration`: custom property values are emitted
      // raw via `_writeFoldedValue` (compressed) or `_writeReindentedValue`
      // (expanded). The parser preserves leading whitespace as part of the
      // value text (e.g., ` #ff0066`), so no extra space is added here.
      val raw = node.value.value match {
        case s: SassString => s.text
        case other         => other.toCssString()
      }
      if (isCompressed) writeFoldedCustomPropertyValue(raw)
      else writeReindentedCustomPropertyValue(raw, node.name.span)
    } else {
      writeSpace()
      recordMapping(node.span)
      buffer.append(formatValue(node.value.value))
    }
    if (node.isImportant) {
      if (isCompressed) buffer.append("!important") else buffer.append(" !important")
    }
  }

  // ---------------------------------------------------------------------------
  // Stage 3: custom property folded/reindented value emission
  // Ported from dart-sass `_writeFoldedValue` / `_writeReindentedValue` /
  // `_minimumIndentation` / `_writeWithIndent` in lib/src/visitor/serialize.dart.
  // Custom properties (`--var`) preserve raw whitespace and newlines from the
  // source. In compressed mode, every newline + following whitespace collapses
  // to a single space. In expanded mode, the value is re-indented relative
  // to the current indentation, dedenting the source's minimum indentation.
  // ---------------------------------------------------------------------------

  /** Folded custom-property value: every `\n` followed by whitespace becomes a single space. */
  private def writeFoldedCustomPropertyValue(text: String): Unit = {
    var i   = 0
    val len = text.length
    while (i < len) {
      val c = text.charAt(i)
      if (c != '\n') {
        buffer.append(c)
        i += 1
      } else {
        buffer.append(' ')
        i += 1
        // Skip following whitespace.
        while (i < len && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '\n' || text.charAt(i) == '\r')) {
          i += 1
        }
      }
    }
  }

  /** Re-indented custom-property value (expanded mode). Dedents the source's minimum indentation and re-indents to the current declaration column. */
  private def writeReindentedCustomPropertyValue(text: String, nameSpan: ssg.sass.util.FileSpan | Null): Unit = {
    val minIndent = minimumIndentation(text)
    if (minIndent == Int.MaxValue) {
      // No newlines: emit verbatim.
      buffer.append(text)
    } else if (minIndent < 0) {
      // Has newlines but no non-empty indented line: trim trailing space.
      buffer.append(trimRight(text))
      buffer.append(' ')
    } else {
      val nameCol = if (nameSpan != null) nameSpan.start.column else 0
      writeWithIndent(text, math.min(minIndent, nameCol))
    }
  }

  /** Returns the minimum indentation level among non-empty lines after the first newline.
    *
    *   - Returns `Int.MaxValue` if [text] has no newlines.
    *   - Returns `-1` if [text] has newlines but no indented non-empty line.
    *   - Otherwise returns the smallest leading-whitespace count.
    */
  private def minimumIndentation(text: String): Int = {
    var i   = 0
    val len = text.length
    // Skip first line.
    while (i < len && text.charAt(i) != '\n') i += 1
    if (i >= len) return Int.MaxValue // No newlines.
    var min   = Int.MaxValue
    var saw   = false
    var atEol = true
    while (i < len) {
      if (atEol) {
        // After a newline: count leading whitespace.
        i += 1 // skip the '\n'
        if (i >= len) {
          // Trailing newline.
          if (!saw) return -1
          return if (min == Int.MaxValue) -1 else min
        }
        var col = 0
        while (i < len && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
          col += 1
          i += 1
        }
        if (i < len && text.charAt(i) != '\n') {
          if (col < min) min = col
          saw = true
        }
        atEol = false
      } else {
        // Scan to next newline.
        while (i < len && text.charAt(i) != '\n') i += 1
        atEol = true
      }
    }
    if (!saw) -1 else min
  }

  private def trimRight(s: String): String = {
    var end = s.length
    while (end > 0 && {
      val c = s.charAt(end - 1)
      c == ' ' || c == '\t' || c == '\n' || c == '\r'
    }) end -= 1
    s.substring(0, end)
  }

  /** Writes [text] to the buffer, replacing [minIndent] leading whitespace on each non-first line with the current indentation. Compresses trailing empty lines into a single trailing space. */
  private def writeWithIndent(text: String, minIndent: Int): Unit = {
    var i   = 0
    val len = text.length
    // Write the first line as-is.
    while (i < len) {
      val c = text.charAt(i)
      i += 1
      if (c == '\n') {
        // First newline: switch to indented-line mode (loop body returns
        // when we hit EOF).
        while (true) {
          // Scan forward to next non-whitespace or EOF.
          var lineStart = i
          var newlines  = 1
          var inner     = true
          while (inner) {
            if (i >= len) {
              // Trailing whitespace: emit a single space and stop.
              buffer.append(' ')
              return
            }
            val ch = text.charAt(i)
            if (ch == ' ' || ch == '\t') {
              i += 1
            } else if (ch == '\n') {
              i += 1
              lineStart = i
              newlines += 1
            } else {
              inner = false
            }
          }
          // Emit `newlines` line feeds, then current indent, then the
          // remainder of the line (skipping `minIndent` of leading ws).
          var n = 0
          while (n < newlines) {
            buffer.append('\n')
            n += 1
          }
          writeIndent()
          val skipFrom = lineStart + math.min(minIndent, i - lineStart)
          // Append from skipFrom to (and including) the next newline or EOF.
          var j = skipFrom
          while (j < len && text.charAt(j) != '\n') {
            buffer.append(text.charAt(j))
            j += 1
          }
          if (j >= len) return
          i = j + 1 // skip the '\n'
        }
      } else {
        buffer.append(c)
      }
    }
  }

  override def visitCssComment(node: CssComment): Unit = {
    // In compressed mode, only preserve /*! comments
    if (isCompressed && !node.isPreserved) return
    // dart-sass serialize.dart:200-202: strip sourceMappingURL and sourceURL comments
    if (node.text.startsWith("/*# source")) {
      val lower = node.text.toLowerCase
      if (lower.startsWith("/*# sourcemappingurl=") || lower.startsWith("/*# sourceurl=")) return
    }

    // dart-sass serialize.dart:204-217: multi-line comment reindentation.
    // When a loud comment spans multiple lines and is inside an indented
    // block in expanded mode, reindent the body using minimumIndentation
    // and writeWithIndent so the output aligns with the current nesting
    // level.
    //
    // Note: indentation before the comment text is emitted by the caller
    // (writeChildrenIn / visitCssStylesheet), so we only handle internal
    // reindentation here.
    val minIndent = minimumIndentation(node.text)
    if (minIndent != Int.MaxValue && minIndent >= 0) {
      // The comment has newlines with indented content; reindent relative
      // to the current indentation and the comment's original start column.
      val effectiveMinIndent = math.min(minIndent, node.span.start.column)
      writeWithIndent(node.text, effectiveMinIndent)
    } else {
      // Single-line comment or no indented content: emit verbatim.
      buffer.append(node.text)
    }
  }

  override def visitCssAtRule(node: CssAtRule): Unit = {
    buffer.append('@')
    buffer.append(node.name.value)
    node.value.foreach { v =>
      buffer.append(' ')
      buffer.append(v.value)
    }
    if (node.isChildless) {
      // Childless at-rules: dart-sass `_visitChildren` is not called, and a
      // semicolon is emitted by the surrounding context via `requiresSemicolon`.
      // We don't append `;` here.
    } else {
      writeSpace()
      writeChildrenIn(node, node.children)
    }
  }

  override def visitCssMediaRule(node: CssMediaRule): Unit = {
    buffer.append("@media")
    // Port of dart-sass visitCssMediaRule / _visitMediaQuery.
    // Space after @media: in compressed mode, only emit when the first
    // query has a modifier, type, or a single "(not ...)" condition.
    val firstQuery = node.queries.head
    if (!isCompressed ||
        firstQuery.modifier.isDefined ||
        firstQuery.type_.isDefined ||
        (firstQuery.conditions.length == 1 &&
            firstQuery.conditions.head.startsWith("(not "))) {
      buffer.append(' ')
    }
    var first = true
    for (query <- node.queries) {
      if (!first) buffer.append(if (isCompressed) "," else ", ")
      first = false
      _visitMediaQuery(query)
    }
    writeSpace()
    writeChildrenIn(node, node.children)
  }

  // Port of dart-sass `_visitMediaQuery` (serialize.dart:307-330).
  private def _visitMediaQuery(query: CssMediaQuery): Unit = {
    query.modifier.foreach { m =>
      buffer.append(m)
      buffer.append(' ')
    }
    query.type_.foreach { t =>
      buffer.append(t)
      if (query.conditions.nonEmpty) buffer.append(" and ")
    }
    // dart-sass serialize.dart:318-321: single "(not ...)" condition gets unwrapped.
    if (query.conditions.length == 1 && query.conditions.head.startsWith("(not ")) {
      buffer.append("not ")
      val cond = query.conditions.head
      buffer.append(cond.substring("(not ".length, cond.length - 1))
    } else {
      val operator = if (query.conjunction) "and" else "or"
      val sep = if (isCompressed) s"$operator " else s" $operator "
      var firstCond = true
      for (cond <- query.conditions) {
        if (!firstCond) buffer.append(sep)
        firstCond = false
        buffer.append(cond)
      }
    }
  }

  override def visitCssSupportsRule(node: CssSupportsRule): Unit = {
    buffer.append("@supports")
    // dart-sass serialize.dart:340-351: in compressed mode, omit the space
    // after `@supports` when the condition starts with `(`.
    val condText = node.condition.value
    if (isCompressed && condText.nonEmpty && condText.charAt(0) == '(') {
      // no space needed — the `(` is unambiguous
    } else {
      buffer.append(' ')
    }
    buffer.append(condText)
    writeSpace()
    writeChildrenIn(node, node.children)
  }

  override def visitCssImport(node: CssImport): Unit = {
    // dart-sass: visitCssImport does NOT emit a trailing `;`. The separator
    // is supplied by `_visitChildren` via `requiresSemicolon`.
    buffer.append("@import")
    writeSpace()
    writeImportUrl(node.url.value)
    node.modifiers.foreach { m =>
      writeSpace()
      buffer.append(m.value)
    }
  }

  /// Writes [url], which is an import's URL, to the buffer.
  ///
  /// Port of dart-sass `_writeImportUrl` (serialize.dart:277-294).
  /// In compressed mode, strips the `url()` wrapper for terser output and
  /// wraps unquoted URLs in quotes.
  private def writeImportUrl(url: String): Unit = {
    if (!isCompressed || url.isEmpty || url.charAt(0) != 'u') {
      buffer.append(url)
      return
    }

    // If this is url(...), remove the surrounding function. This is terser and
    // it allows us to remove whitespace between `@import` and the URL.
    val urlContents = url.substring(4, url.length - 1)

    val maybeQuote = urlContents.charAt(0)
    if (maybeQuote == '\'' || maybeQuote == '"') {
      buffer.append(urlContents)
    } else {
      // If the URL didn't contain quotes, write them manually.
      buffer.append('"')
      buffer.append(urlContents)
      buffer.append('"')
    }
  }

  override def visitCssKeyframeBlock(node: CssKeyframeBlock): Unit = {
    buffer.append(node.selector.value.mkString(", "))
    writeSpace()
    writeChildrenIn(node, node.children)
  }
}

object SerializeVisitor {

  /** Convenience entry point: serialize a [[CssStylesheet]] using default options. */
  def serialize(node: CssStylesheet): SerializeResult =
    new SerializeVisitor().serialize(node)

  /** Serialize compressed (minified). */
  def serializeCompressed(node: CssStylesheet): SerializeResult =
    new SerializeVisitor(style = OutputStyle.Compressed).serialize(node)

  /** Convert a [[Value]] to its CSS text form using the same formatting
    * rules the full stylesheet serializer uses for declarations.
    *
    * When `inspect = true`, matches dart-sass's `serializeValue(v,
    * inspect: true)` entry point used by `meta.inspect()`: nested lists
    * get their separator-preserving parentheses, empty lists render as
    * `()`, single-element comma lists as `(x,)`, colors use inspect
    * representation, etc.
    *
    * Ported from dart-sass `serializeValue` in serialize.dart.
    */
  def serializeValue(value: Value, inspect: Boolean = false): String = {
    val visitor = new SerializeVisitor(inspect = inspect)
    visitor.formatValuePublic(value)
  }

  /** Renders `number` in the way dart-sass does before [[removeExponent]] runs.
    *
    * Dart's `double.toString` yields `1.0`, `-3.14`, `1e+21`, etc. JVM/JS/Native `Double.toString` is very close but varies slightly on each platform — we normalise to the format the port expects
    * (lowercase `e`, trailing `.0` stripped for integer-valued doubles so [[removeExponent]] can round-trip).
    */
  def doubleToString(number: Double): String = {
    val raw = java.lang.Double.toString(number)
    // Java uses uppercase `E` for exponents; dart uses lowercase `e`.
    var s = if (raw.indexOf('E') >= 0) raw.replace('E', 'e') else raw
    // Java always writes a `.0` for integer-valued doubles (`1.0`, `1.0e21`).
    // Dart's `_removeExponent` was written assuming dart's output format,
    // which drops the redundant `.0` before the exponent (`1e21`) but keeps
    // it for non-exponential integers (`1.0`). Strip the `.0` just before
    // `e` so the algorithm round-trips correctly.
    val eIdx = s.indexOf('e')
    if (eIdx >= 2 && s.charAt(eIdx - 2) == '.' && s.charAt(eIdx - 1) == '0') {
      s = s.substring(0, eIdx - 2) + s.substring(eIdx)
    }
    s
  }

  /** If `text` uses exponent notation, returns an equivalent non-exponent representation. Otherwise returns `text`.
    *
    * Port of dart-sass `_removeExponent` in serialize.dart.
    */
  def removeExponent(text: String): String = {
    var eIdx = -1
    var i    = 0
    while (eIdx < 0 && i < text.length) {
      if (text.charAt(i) == 'e') eIdx = i
      i += 1
    }
    if (eIdx < 0) return text

    val negative = text.charAt(0) == '-'

    // Parse the exponent after `e`, tolerating an optional leading `+`.
    var expStart = eIdx + 1
    if (expStart < text.length && text.charAt(expStart) == '+') expStart += 1
    val exponent = text.substring(expStart).toInt

    // `digits` collects the significant digits (including the leading sign).
    // Dart's algorithm writes char 0, skips char 1 (which is `.` if there's
    // more than one significant digit), then writes the rest up to `e`.
    val digits = new StringBuilder()
    digits.append(text.charAt(0))
    if (negative) {
      if (eIdx > 1) digits.append(text.charAt(1))
      if (eIdx > 3) digits.append(text.substring(3, eIdx))
    } else {
      if (eIdx > 2) digits.append(text.substring(2, eIdx))
    }

    if (exponent > 0) {
      // Number of trailing zeros needed after the significant digits.
      // Negative means the decimal point falls *inside* the digit string.
      val additionalZeroes = exponent - (digits.length - 1 - (if (negative) 1 else 0))
      if (additionalZeroes >= 0) {
        var k = 0
        while (k < additionalZeroes) { digits.append('0'); k += 1 }
        digits.toString()
      } else {
        // Java uses scientific notation earlier than Dart (>= 10^7 vs 10^21),
        // so we may need to reinsert a decimal point within the digit string.
        val insertPos = if (negative) exponent + 2 else exponent + 1
        digits.insert(insertPos, '.')
        digits.toString()
      }
    } else {
      val result = new StringBuilder()
      if (negative) result.append('-')
      result.append("0.")
      var k = -1
      while (k > exponent) { result.append('0'); k -= 1 }
      if (negative) result.append(digits.toString().substring(1))
      else result.append(digits.toString())
      result.toString()
    }
  }

  // ---------------------------------------------------------------------------
  // VLQ base64 encoding (source map v3 mapping segments)
  // ---------------------------------------------------------------------------

  private val vlqAlphabet: Array[Char] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray

  /** VLQ-encode a single signed integer into the base64 form used by source maps. */
  def vlqEncode(value: Int): String = {
    var v    = if (value < 0) (-value << 1) | 1 else value << 1
    val sb   = new StringBuilder()
    var more = true
    while (more) {
      var digit = v & 0x1f
      v >>>= 5
      if (v > 0) digit |= 0x20 else more = false
      sb.append(vlqAlphabet(digit))
    }
    sb.toString()
  }
}
