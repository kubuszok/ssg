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
  def write(obj:          Any):                      Unit
  def writeCharCode(code: Int):                      Unit
  def writeln(obj:        Any = ""):                 Unit
  def forSpan[T](span:    FileSpan)(callback: => T): T
  def length:                                        Int
  def isEmpty:                                       Boolean
  def isNotEmpty:                                    Boolean = !isEmpty
  override def toString:                             String
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

  override def length: Int = buffer.length

  override def isEmpty: Boolean = buffer.isEmpty

  override def toString: String = buffer.toString()
}

/** A buffer that tracks source spans for source map generation. */
final class SourceMapBuffer extends SassBuffer {

  private val buffer = new StringBuilder()
  private var _line:   Int = 0
  private var _column: Int = 0
  private val entries = scala.collection.mutable.ArrayBuffer.empty[(FileSpan, Int, Int)]
  private var inSpan: Boolean = false

  override def write(obj: Any): Unit = {
    val str = obj.toString
    _trackNewlines(str)
    buffer.append(str)
  }

  override def writeCharCode(code: Int): Unit = {
    buffer.append(code.toChar)
    if (code == CharCode.$lf) {
      _line += 1
      _column = 0
    } else {
      _column += 1
    }
  }

  override def writeln(obj: Any = ""): Unit = {
    write(obj)
    writeCharCode(CharCode.$lf)
  }

  override def forSpan[T](span: FileSpan)(callback: => T): T = {
    if (!inSpan) entries += ((span, _line, _column))
    val wasInSpan = inSpan
    inSpan = true
    val result = callback
    inSpan = wasInSpan
    result
  }

  override def length: Int = buffer.length

  override def isEmpty: Boolean = buffer.isEmpty

  override def toString: String = buffer.toString()

  private def _trackNewlines(str: String): Unit = {
    var i = 0
    while (i < str.length) {
      if (str.charAt(i) == '\n') {
        _line += 1
        _column = 0
      } else {
        _column += 1
      }
      i += 1
    }
  }
}
