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
import ssg.sass.ast.css.{ CssAtRule, CssComment, CssDeclaration, CssImport, CssKeyframeBlock, CssMediaRule, CssNode, CssStyleRule, CssStylesheet, CssSupportsRule }
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
    buffer.append('{')
    writeLine()
    indentLevel += 1
    var first = true
    for (child <- children) {
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

  /** Formats a SassNumber: in compressed mode strips a leading `0` from values like `0.5px` -> `.5px` (and `-0.5` -> `-.5`). Trailing-zero stripping is already handled by `SassNumber.formatNumber`.
    */
  private def formatSassNumber(n: SassNumber): String = {
    val s = n.toCssString()
    if (!isCompressed) s
    else if (s.startsWith("0.")) s.substring(1)
    else if (s.startsWith("-0.")) "-" + s.substring(2)
    else s
  }

  /** Formats a SassColor in the rgb space as `#hex` / `#abc` shorthand or a named color when shorter. Falls back to rgba(...) when alpha < 1.
    */
  private def formatColor(c: SassColor): String = {
    val a = c.alphaOrNull.getOrElse(1.0)
    val r = math.round(c.channel0).toInt
    val g = math.round(c.channel1).toInt
    val b = math.round(c.channel2).toInt
    if (a < 1.0) {
      // Defer to default rendering for non-opaque colors.
      c.toCssString()
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
        // Pick the shortest of: short hex, full hex (only if no shorthand), name
        val candidates = List(short) ++ name.toList
        candidates.minBy(_.length)
      } else {
        // Expanded mode: use shorthand when available, prefer name only if strictly shorter than short.
        name match {
          case Some(n) if n.length < short.length => n
          case _                                  => short
        }
      }
    }
  }

  override def visitCssStylesheet(node: CssStylesheet): Unit = {
    var first = true
    for (child <- node.children) {
      if (!first) writeLine()
      first = false
      child.accept(this)
    }
    if (!isCompressed && node.children.nonEmpty) buffer.append('\n')
  }

  override def visitCssStyleRule(node: CssStyleRule): Unit = {
    // Selector is stored as Any (placeholder until selector AST is wired)
    recordMapping(node.span)
    buffer.append(node.selector.toString)
    writeSpace()
    writeChildren(node.children)
  }

  override def visitCssDeclaration(node: CssDeclaration): Unit = {
    recordMapping(node.span)
    buffer.append(node.name.value)
    buffer.append(':')
    writeSpace()
    buffer.append(formatValue(node.value.value))
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
