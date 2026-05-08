/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/no_source_map_buffer.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: no_source_map_buffer.dart + source_map_buffer.dart → SourceMapBuffer.scala
 *   Convention: Trait + implementation; full SourceMapBuffer deferred until source map support needed
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/no_source_map_buffer.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package util

/** Trait for CSS output buffers, with optional source map tracking. */
trait SassBuffer {
  def write(obj:          Any):                             Unit
  def writeCharCode(code: Int):                             Unit
  def writeln(obj:        Any = ""):                        Unit
  def writeAll(objs:      Iterable[Any], sep: String = ""): Unit    = write(objs.mkString(sep))
  def forSpan[T](span:    FileSpan)(callback: => T):        T
  def length:                                               Int
  def isEmpty:                                              Boolean
  def isNotEmpty:                                           Boolean = !isEmpty
  override def toString:                                    String

  final def append(obj: Any):  SassBuffer = { write(obj); this }
  final def append(c:   Char): SassBuffer = { writeCharCode(c.toInt); this }
}

/** A buffer that outputs CSS without tracking source maps. */
final class NoSourceMapBuffer extends SassBuffer {

  private val buffer = new StringBuilder()

  override def write(obj: Any): Unit = buffer.append(obj.toString)

  override def writeCharCode(code: Int): Unit = buffer.append(code.toChar)

  override def writeln(obj: Any = ""): Unit = {
    buffer.append(obj.toString)
    buffer.append('\n')
  }

  override def forSpan[T](span: FileSpan)(callback: => T): T = callback

  def clear():                             Unit   = throw new UnsupportedOperationException("NoSourceMapBuffer.clear()")
  def buildSourceMap(prefix: String = ""): String =
    throw new UnsupportedOperationException("NoSourceMapBuffer.buildSourceMap()")

  override def length: Int = buffer.length

  override def isEmpty: Boolean = buffer.isEmpty

  override def toString: String = buffer.toString()
}

/** A buffer that tracks source spans for source map generation.
  *
  * Port of dart-sass `SourceMapBuffer` (lib/src/util/source_map_buffer.dart).
  */
final class SourceMapBuffer extends SassBuffer {

  private val buffer = new StringBuilder()
  private var _line:   Int     = 0
  private var _column: Int     = 0
  private var _inSpan: Boolean = false

  /** Source map entries: (sourceUrl, sourceLine, sourceCol, targetLine, targetCol). */
  private val _entries = scala.collection.mutable.ArrayBuffer.empty[(Nullable[String], Int, Int, Int, Int)]

  override def write(obj: Any): Unit = {
    val str = obj.toString
    var i   = 0
    while (i < str.length) {
      if (str.charAt(i) == '\n') _writeLine()
      else _column += 1
      i += 1
    }
    buffer.append(str)
  }

  override def writeCharCode(code: Int): Unit = {
    buffer.append(code.toChar)
    if (code == CharCode.$lf) _writeLine()
    else _column += 1
  }

  override def writeln(obj: Any = ""): Unit = {
    val str = obj.toString
    if (str.nonEmpty) write(str)
    writeCharCode(CharCode.$lf)
  }

  override def writeAll(objs: Iterable[Any], sep: String = ""): Unit =
    write(objs.mkString(sep))

  def clear(): Unit =
    throw new UnsupportedOperationException("SourceMapBuffer.clear() is not supported.")

  override def forSpan[T](span: FileSpan)(callback: => T): T = {
    _addEntry(span)
    val wasInSpan = _inSpan
    _inSpan = true
    try callback
    finally _inSpan = wasInSpan
  }

  private def _addEntry(span: FileSpan): Unit = {
    val srcUrl  = span.sourceUrl
    val srcLine = span.start.line
    val srcCol  = span.start.column
    if (_entries.nonEmpty) {
      val last = _entries.last
      if (last._2 == srcLine && last._4 == _line) return
      if (last._4 == _line && last._5 == _column) return
    }
    _entries += ((srcUrl, srcLine, srcCol, _line, _column))
  }

  private def _writeLine(): Unit = {
    if (_entries.nonEmpty) {
      val last = _entries.last
      if (last._4 == _line && last._5 == _column) {
        _entries.remove(_entries.length - 1)
      }
    }
    _line += 1
    _column = 0
    if (_inSpan && _entries.nonEmpty) {
      val prev = _entries.last
      _entries += ((prev._1, prev._2, prev._3, _line, _column))
    }
  }

  override def length: Int = buffer.length

  override def isEmpty: Boolean = buffer.isEmpty

  override def toString: String = buffer.toString()

  /** Returns the collected source map entries as a list of (sourceUrl, sourceLine, sourceCol, targetLine, targetCol) tuples.
    */
  def sourceMapEntries: List[(Nullable[String], Int, Int, Int, Int)] = _entries.toList

  /** Builds a VLQ-encoded source map JSON string.
    *
    * Port of dart-sass `buildSourceMap()`. The optional [prefix] adjusts target positions — useful when the CSS output is prepended with a string not tracked by this buffer.
    */
  def buildSourceMap(prefix: String = ""): String = {
    val prefixLines   = prefix.count(_ == '\n')
    val prefixLastCol = prefix.length - prefix.lastIndexOf('\n') - 1

    val sources      = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    val mappingLines = scala.collection.mutable.ArrayBuffer.empty[scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]]

    for ((srcUrl, srcLine, srcCol, tgtLine, tgtCol) <- _entries) {
      val adjustedLine = tgtLine + prefixLines
      val adjustedCol  = if (tgtLine == 0) tgtCol + prefixLastCol else tgtCol
      val srcIdx       = srcUrl.fold(-1) { url =>
        sources.getOrElseUpdate(url, sources.size)
      }
      if (srcIdx >= 0) {
        while (mappingLines.length <= adjustedLine)
          mappingLines += scala.collection.mutable.ArrayBuffer.empty
        mappingLines(adjustedLine) += ((adjustedCol, srcIdx, srcLine, srcCol))
      }
    }

    val mappingsStr = new StringBuilder()
    var prevGenCol  = 0; var prevSrcIdx = 0; var prevSrcLine = 0; var prevSrcCol = 0
    for (lineIdx <- mappingLines.indices) {
      if (lineIdx > 0) mappingsStr.append(';')
      prevGenCol = 0
      val segs = mappingLines(lineIdx)
      for (segIdx <- segs.indices) {
        if (segIdx > 0) mappingsStr.append(',')
        val (genCol, srcIdx, srcLine, srcCol) = segs(segIdx)
        _vlqEncode(mappingsStr, genCol - prevGenCol); prevGenCol = genCol
        _vlqEncode(mappingsStr, srcIdx - prevSrcIdx); prevSrcIdx = srcIdx
        _vlqEncode(mappingsStr, srcLine - prevSrcLine); prevSrcLine = srcLine
        _vlqEncode(mappingsStr, srcCol - prevSrcCol); prevSrcCol = srcCol
      }
    }

    val srcList = sources.keys.toList
    val srcJson = srcList.map(s => "\"" + _jsonEscape(s) + "\"").mkString("[", ",", "]")
    s"""{"version":3,"sourceRoot":"","sources":$srcJson,"names":[],"mappings":"${mappingsStr.toString()}"}"""
  }

  private val VlqChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

  private def _vlqEncode(sb: StringBuilder, value: Int): Unit = {
    var v        = if (value < 0) ((-value) << 1) | 1 else value << 1
    var continue = true
    while (continue) {
      var digit = v & 0x1f
      v >>>= 5
      if (v > 0) digit |= 0x20
      sb.append(VlqChars.charAt(digit))
      continue = v > 0
    }
  }

  private def _jsonEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}
