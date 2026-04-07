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
import ssg.sass.util.NumberUtil
import ssg.sass.value.{ SassColor, SassNumber, Value }
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

  private def writeChildren(children: List[CssNode]): Unit = {
    val visible = children.filter(c => !isNodeInvisible(c))
    buffer.append('{')
    writeLine()
    indentLevel += 1
    var first = true
    for (child <- visible) {
      if (!first && !isCompressed) writeLine()
      first = false
      writeIndent()
      child.accept(this)
    }
    indentLevel -= 1
    writeLine()
    writeIndent()
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
    case c: SassColor if c.space eq ColorSpace.rgb => formatColor(c)
    case n: SassNumber                             => formatSassNumber(n)
    case _ => v.toCssString()
  }

  /** Formats a SassNumber for CSS output.
    *
    * Ported from dart-sass `_SerializeVisitor.visitNumber` (serialize.dart):
    * the numeric portion is written via [[writeNumberTo]] (the faithful port
    * of `_writeNumber`), then the single numerator unit — if any — is
    * appended. Complex units and non-finite values fall back to the existing
    * `SassNumber.toCssString()` which wraps them in `calc(...)`.
    */
  private def formatSassNumber(n: SassNumber): String = {
    if (!n.value.isFinite) return n.toCssString()
    if (n.hasComplexUnits) return n.toCssString()
    val sb = new StringBuilder()
    writeNumberTo(sb, n.value)
    if (n.numeratorUnits.nonEmpty) sb.append(n.numeratorUnits.head)
    sb.toString()
  }

  /** Writes `number` to `sb` without exponent notation and with at most
    * `SassNumber.precision` digits after the decimal point.
    *
    * Ported from dart-sass `_writeNumber` / `_removeExponent` / `_writeRounded`
    * in lib/src/visitor/serialize.dart. In compressed mode, strips the leading
    * `0` from values like `0.5` -> `.5` (and `-0.5` -> `-.5`). Emits integers
    * without a trailing `.0`. Suppresses the minus sign when a negative value
    * rounds to exactly zero.
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

  /** Rounds `text` (a number written without exponent notation) to
    * [[SassNumber.precision]] digits after the decimal point and writes the
    * result to `sb`. Direct port of dart-sass `_writeRounded`.
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
    // dart-sass separates top-level siblings with a blank line in expanded mode
    // (i.e. the closing `}` of one rule is followed by `\n\n` before the next
    // rule starts). In compressed mode nothing is written between siblings.
    val visible        = node.children.filter(c => !isNodeInvisible(c))
    var first          = true
    var prevWasComment = false
    for (child <- visible) {
      if (!first) {
        if (isCompressed) ()
        else {
          // Single newline after a loud/preserved comment, blank line otherwise,
          // to match dart-sass top-level spacing conventions.
          buffer.append('\n')
          if (!prevWasComment) buffer.append('\n')
        }
      }
      first = false
      prevWasComment = child.isInstanceOf[CssComment]
      child.accept(this)
    }
    if (!isCompressed && visible.nonEmpty) buffer.append('\n')
  }

  override def visitCssStyleRule(node: CssStyleRule): Unit = {
    // Selector is stored as Any (placeholder until selector AST is wired)
    recordMapping(node.span)
    buffer.append(node.selector.toString)
    writeSpace()
    writeChildren(node.children)
  }

  override def visitCssDeclaration(node: CssDeclaration): Unit = {
    // Record one mapping for the property name and a second for the value
    // so debuggers can highlight either side of the `name: value;` pair.
    recordMapping(node.span)
    buffer.append(node.name.value)
    buffer.append(':')
    writeSpace()
    recordMapping(node.span)
    buffer.append(formatValue(node.value.value))
    if (node.isImportant) {
      if (isCompressed) buffer.append("!important") else buffer.append(" !important")
    }
    buffer.append(';')
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
      buffer.append(';')
    } else {
      writeSpace()
      writeChildren(node.children)
    }
  }

  override def visitCssMediaRule(node: CssMediaRule): Unit = {
    buffer.append("@media ")
    buffer.append(node.queries.mkString(", "))
    writeSpace()
    writeChildren(node.children)
  }

  override def visitCssSupportsRule(node: CssSupportsRule): Unit = {
    buffer.append("@supports ")
    buffer.append(node.condition.value)
    writeSpace()
    writeChildren(node.children)
  }

  override def visitCssImport(node: CssImport): Unit = {
    buffer.append("@import ")
    buffer.append(node.url.value)
    node.modifiers.foreach { m =>
      buffer.append(' ')
      buffer.append(m.value)
    }
    buffer.append(';')
  }

  override def visitCssKeyframeBlock(node: CssKeyframeBlock): Unit = {
    buffer.append(node.selector.value.mkString(", "))
    writeSpace()
    writeChildren(node.children)
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
    * Dart's `double.toString` yields `1.0`, `-3.14`, `1e+21`, etc. JVM/JS/Native
    * `Double.toString` is very close but varies slightly on each platform — we
    * normalise to the format the port expects (lowercase `e`, trailing `.0`
    * stripped for integer-valued doubles so [[removeExponent]] can round-trip).
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

  /** If `text` uses exponent notation, returns an equivalent non-exponent
    * representation. Otherwise returns `text`.
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
      var k = 0
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
