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
import ssg.sass.ast.css.{ CssAtRule, CssComment, CssDeclaration, CssImport, CssKeyframeBlock, CssMediaRule, CssNode, CssParentNode, CssStyleRule, CssStylesheet, CssSupportsRule }
import ssg.sass.ast.selector.{ ComplexSelector, SelectorList }
import ssg.sass.util.NumberUtil
import ssg.sass.value.{ ListSeparator, SassColor, SassList, SassMap, SassNumber, SassString, Value }
import ssg.sass.value.color.ColorSpace

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
    case p: CssParentNode  =>
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

  /** Serialize the given stylesheet to CSS text. */
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
    val mapJson: Nullable[String] =
      if (sourceMap) Nullable(buildSourceMapJson()) else Nullable.empty[String]
    SerializeResult(buffer.toString(), sourceMap = mapJson)
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
          // dart-sass: extra blank line if previous closes a "group" (rule).
          if (previous != null && previous.isGroupEnd) writeLine()
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
    case _             => v.toCssString()
  }

  // ---------------------------------------------------------------------------
  // Stage 2: modern color space dispatch
  // Ported from dart-sass `visitColor` in lib/src/visitor/serialize.dart.
  // The dispatch is:
  //   - Legacy spaces (rgb/hsl/hwb) with no missing channels  → legacy handler
  //   - rgb with any missing channel                          → modern rgb()
  //   - hsl/hwb with any missing channel                      → modern hsl()/hwb()
  //   - lab/lch/oklab/oklch                                   → modern function syntax
  //   - everything else (xyz, display-p3, ...)                → color() function
  // The modern-syntax emission for non-legacy spaces is delegated to
  // `SassColor.toCssString()` which already implements dart-sass's per-space
  // formatting (channel/none/slash-alpha). This stage adds the dispatch and
  // the legacy-vs-modern selection.
  // ---------------------------------------------------------------------------
  private def formatColorDispatch(c: SassColor): String = {
    val space      = c.space
    val anyMissing =
      c.isChannel0Missing || c.isChannel1Missing || c.isChannel2Missing || c.isAlphaMissing
    if ((space eq ColorSpace.rgb) && !anyMissing) {
      // Legacy hex/name emission (#fff / red / rgba(...)) is only valid for
      // the rgb color space — hsl and hwb fall through to their own modern
      // function-form serialization via SassColor.toCssString.
      formatColor(c)
    } else if ((space eq ColorSpace.rgb) && anyMissing) {
      formatModernRgb(c)
    } else {
      // hsl/hwb/lab/lch/oklab/oklch and color()-function spaces:
      // SassColor.toCssString() already emits modern syntax with `none`
      // propagation and `/ alpha`.
      val raw = c.toCssString()
      if (isCompressed) compactColorFunction(raw) else raw
    }
  }

  /** Emits an `rgb(R G B)` or `rgb(R G B / A)` modern function call. Used when at least one channel is `none`. */
  private def formatModernRgb(c: SassColor): String = {
    val sb = new StringBuilder()
    sb.append("rgb(")
    sb.append(channelOrNone(c.channel0OrNull))
    sb.append(' ')
    sb.append(channelOrNone(c.channel1OrNull))
    sb.append(' ')
    sb.append(channelOrNone(c.channel2OrNull))
    appendSlashAlpha(sb, c)
    sb.append(')')
    sb.toString()
  }

  private def channelOrNone(ch: Nullable[Double]): String = {
    val sb = new StringBuilder()
    ch.foreach { v =>
      writeNumberTo(sb, v)
    }
    if (sb.isEmpty) "none" else sb.toString()
  }

  private def appendSlashAlpha(sb: StringBuilder, c: SassColor): Unit = {
    val a = c.alphaOrNull
    if (a.isEmpty) {
      sb.append(" / none")
    } else {
      val v = a.getOrElse(1.0)
      if (!NumberUtil.fuzzyEquals(v, 1.0)) {
        if (isCompressed) sb.append("/") else sb.append(" / ")
        val tmp = new StringBuilder()
        writeNumberTo(tmp, v)
        sb.append(tmp.toString())
      }
    }
  }

  /** Drops optional spaces around `/` and after `,` for compressed-mode color-function output. */
  private def compactColorFunction(s: String): String = {
    val sb = new StringBuilder(s.length)
    var i  = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == ' ' && i + 1 < s.length && (s.charAt(i + 1) == '/' || s.charAt(i + 1) == ')')) {
        // skip space before `/` or `)`
      } else if (c == '/' && i + 1 < s.length && s.charAt(i + 1) == ' ') {
        sb.append('/')
        i += 1 // skip the trailing space too
      } else {
        sb.append(c)
      }
      i += 1
    }
    sb.toString()
  }

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
      // Fold newlines to a single space to avoid breaking declarations.
      val sb = new StringBuilder()
      var i  = 0
      while (i < s.text.length) {
        val c = s.text.charAt(i)
        if (c == '\n') sb.append(' ')
        else sb.append(c)
        i += 1
      }
      sb.toString()
    } else {
      var hasDouble = false
      var hasSingle = false
      var i         = 0
      while (i < s.text.length) {
        val c = s.text.charAt(i)
        if (c == '"') hasDouble = true
        else if (c == '\'') hasSingle = true
        i += 1
      }
      val q  = if (hasDouble && !hasSingle) '\'' else '"'
      val sb = new StringBuilder()
      sb.append(q)
      i = 0
      while (i < s.text.length) {
        val c = s.text.charAt(i)
        c match {
          case '\\'                       => sb.append("\\\\")
          case _ if c == q                => sb.append('\\'); sb.append(c)
          case _ if c < 0x20 || c == 0x7f =>
            sb.append('\\')
            sb.append(Integer.toHexString(c.toInt))
            sb.append(' ')
          case _ => sb.append(c)
        }
        i += 1
      }
      sb.append(q)
      sb.toString()
    }

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
    val elems =
      if (l.separator == ListSeparator.Comma || l.hasBrackets) l.asList
      else l.asList.filterNot(_.isBlank)
    val sepStr = l.separator match {
      case ListSeparator.Comma     => if (isCompressed) "," else ", "
      case ListSeparator.Space     => " "
      case ListSeparator.Slash     => if (isCompressed) "/" else " / "
      case ListSeparator.Undecided => " "
    }
    val inner = elems.map(formatValue).mkString(sepStr)
    val body  =
      if (l.asList.length == 1 && l.separator == ListSeparator.Comma) s"($inner,)"
      else inner
    if (l.hasBrackets) s"[$body]"
    else body
  }

  private def formatMap(m: SassMap): String = {
    val sep     = if (isCompressed) "," else ", "
    val entries = m.contents.map { case (k, v) => s"${formatValue(k)}:${if (isCompressed) "" else " "}${formatValue(v)}" }.mkString(sep)
    s"($entries)"
  }

  /** Formats a SassNumber for CSS output.
    *
    * Ported from dart-sass `_SerializeVisitor.visitNumber` (serialize.dart): the numeric portion is written via [[writeNumberTo]] (the faithful port of `_writeNumber`), then the single numerator unit
    * — if any — is appended. Complex units and non-finite values fall back to the existing `SassNumber.toCssString()` which wraps them in `calc(...)`.
    */
  private def formatSassNumber(n: SassNumber): String = {
    if (!n.value.isFinite) return n.toCssString()
    if (n.hasComplexUnits) return n.toCssString()
    val sb = new StringBuilder()
    writeNumberTo(sb, n.value)
    if (n.numeratorUnits.nonEmpty) sb.append(n.numeratorUnits.head)
    sb.toString()
  }

  /** Writes `number` to `sb` without exponent notation and with at most `SassNumber.precision` digits after the decimal point.
    *
    * Ported from dart-sass `_writeNumber` / `_removeExponent` / `_writeRounded` in lib/src/visitor/serialize.dart. In compressed mode, strips the leading `0` from values like `0.5` -> `.5` (and
    * `-0.5` -> `-.5`). Emits integers without a trailing `.0`. Suppresses the minus sign when a negative value rounds to exactly zero.
    */
  private[visitor] def writeNumberTo(sb: StringBuilder, number: Double): Unit = {
    // Clamp doubles that are fuzzy-equal to an integer to their integer value.
    // In inspect mode only clamp on exact equality so full precision is shown.
    val asInt = NumberUtil.fuzzyAsInt(number)
    if (asInt.isDefined && (!inspect || number == asInt.get.toDouble)) {
      sb.append(SerializeVisitor.removeExponent(asInt.get.toString))
      return
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
      var done = false
      while (!done) {
        digits(digitsIndex - 1) += 1
        if (digits(digitsIndex - 1) != 10) done = true
        else digitsIndex -= 1
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

    // Write the digits before the decimal. Omit the leading `0` placeholder
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

  /** Formats a SassColor in the rgb space as `#hex` / `#abc` shorthand or a named color when shorter. Falls back to rgba(...) when alpha < 1.
    */
  private def formatColor(c: SassColor): String = {
    val a = c.alphaOrNull.getOrElse(1.0)
    val r = math.round(c.channel0).toInt
    val g = math.round(c.channel1).toInt
    val b = math.round(c.channel2).toInt
    if (a < 1.0) {
      // Render non-opaque legacy RGB colors as `rgba(r, g, b, a)`. The alpha
      // is formatted with trailing zeros stripped (e.g. `0.5` not `0.50`).
      val alphaStr = {
        val s = "%s".format(a)
        if (s.contains('.')) s.replaceAll("0+$", "").replaceAll("\\.$", "")
        else s
      }
      val sep = if (isCompressed) "," else ", "
      s"rgba($r$sep$g$sep$b$sep$alphaStr)"
    } else if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
      c.toCssString()
    } else {
      val hex   = "#%02x%02x%02x".format(r, g, b)
      val short =
        if (hex.charAt(1) == hex.charAt(2) && hex.charAt(3) == hex.charAt(4) && hex.charAt(5) == hex.charAt(6))
          "#" + hex.charAt(1) + hex.charAt(3) + hex.charAt(5)
        else hex
      val name = ColorNames.namesByColor.get(c)
      if (isCompressed) {
        // Pick the shortest of: short hex, full hex (only if no shorthand), name.
        // Prefer name on tie to match dart-sass.
        val candidates = name.toList ++ List(short)
        candidates.minBy(_.length)
      } else {
        // Expanded mode: use shorthand when available, prefer name only if strictly shorter than short.
        name match {
          case Some(n) if n.length <= short.length => n
          case _                                   => short
        }
      }
    }
  }

  override def visitCssStylesheet(node: CssStylesheet): Unit = {
    // Top-level siblings: dart-sass `visitCssStylesheet`. Between visible
    // siblings emit a line feed (or trailing-comment space), and an extra
    // line feed when the previous sibling has `isGroupEnd == true`.
    //
    // Note: ssg-sass's evaluator does not yet propagate `isGroupEnd` from
    // the original source position (a flag dart-sass sets when nested
    // blocks are flattened). To preserve the historical output where
    // top-level rules and at-rules are separated by a blank line in
    // expanded mode, we conservatively emit the second line feed for any
    // non-comment-following-non-comment pair. This matches dart-sass's
    // observable output for typical inputs without requiring AST changes.
    var previous: CssNode | Null = null
    for (child <- node.children) {
      if (!isNodeInvisible(child)) {
        if (previous != null) {
          if (requiresSemicolon(previous)) buffer.append(';')
          if (isTrailingComment(child, previous)) {
            writeSpace()
          } else {
            writeLine()
            // Emit a blank line between non-comment top-level siblings
            // (or when the previous node carries the explicit isGroupEnd
            // marker) to match dart-sass's typical top-level output.
            if (
              previous.isGroupEnd ||
              (!previous.isInstanceOf[CssComment] && !child.isInstanceOf[CssComment])
            ) writeLine()
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
    // Selector is stored as Any (either a SelectorList once the selector AST
    // is wired, or a String text fallback used by the current evaluator).
    recordMapping(node.span)
    node.selector match {
      case list: SelectorList => buffer.append(formatSelectorList(list))
      case s:    String       => buffer.append(s)
      case other => buffer.append(other.toString)
    }
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
    for (complex <- list.components) {
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
      // ssg-sass's parser strips the leading whitespace after `:` before
      // capturing the raw custom-property value, while dart-sass preserves
      // it as part of the value text. Add a single space here in expanded
      // mode to keep `--foo: value` round-tripping correctly. In compressed
      // mode, dart-sass also drops the space (the folded value's leading
      // newline collapses to nothing if no content precedes it).
      val raw = node.value.value match {
        case s: SassString => s.text
        case other         => other.toCssString()
      }
      if (isCompressed) writeFoldedCustomPropertyValue(raw)
      else {
        // Re-prepend the missing leading space so the output matches the
        // canonical `--foo: value` form. The reindented helper assumes the
        // first line is emitted verbatim; that's still correct because we
        // append the space first.
        writeSpace()
        writeReindentedCustomPropertyValue(raw, node.name.span)
      }
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
    buffer.append(node.text)
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
    buffer.append("@media ")
    buffer.append(node.queries.mkString(", "))
    writeSpace()
    writeChildrenIn(node, node.children)
  }

  override def visitCssSupportsRule(node: CssSupportsRule): Unit = {
    buffer.append("@supports ")
    buffer.append(node.condition.value)
    writeSpace()
    writeChildrenIn(node, node.children)
  }

  override def visitCssImport(node: CssImport): Unit = {
    // dart-sass: visitCssImport does NOT emit a trailing `;`. The separator
    // is supplied by `_visitChildren` via `requiresSemicolon`.
    buffer.append("@import ")
    buffer.append(node.url.value)
    node.modifiers.foreach { m =>
      buffer.append(' ')
      buffer.append(m.value)
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
      // Append `exponent - (significantDigitsAfterFirst)` zeros.
      val additionalZeroes = exponent - (digits.length - 1 - (if (negative) 1 else 0))
      var k                = 0
      while (k < additionalZeroes) { digits.append('0'); k += 1 }
      digits.toString()
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
