/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/interpolation_buffer.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: interpolation_buffer.dart -> InterpolationBuffer.scala
 *   Convention: Dart StringSink -> plain `write`/`append` methods
 *   Idiom: holds mutable state with a StringBuilder + ListBuffer, matching
 *     the Dart implementation's contract.
 */
package ssg
package sass

import ssg.sass.ast.sass.{ Expression, Interpolation }
import ssg.sass.util.FileSpan
import ssg.sass.Nullable

import scala.collection.mutable

/** A buffer that iteratively builds up an [[Interpolation]].
  *
  * Add text using [[write]] and related methods, and [[Expression]]s using [[add]]. Once that's done, call [[interpolation]] to build the result.
  */
final class InterpolationBuffer {

  /** The buffer that accumulates plain text. */
  private val text: StringBuilder = new StringBuilder()

  /** The contents of the [[Interpolation]] so far (Strings and Expressions). */
  private val contents: mutable.ListBuffer[Any] = mutable.ListBuffer.empty

  /** The spans of the expressions in [[contents]]. */
  private val spans: mutable.ListBuffer[Nullable[FileSpan]] = mutable.ListBuffer.empty

  /** Returns whether this buffer has no contents. */
  def isEmpty: Boolean = contents.isEmpty && text.isEmpty

  /** Returns the substring of the buffer string after the last interpolation. */
  def trailingString: String = text.toString

  /** Empties this buffer. */
  def clear(): Unit = {
    contents.clear()
    text.clear()
    spans.clear()
  }

  /** Appends [s] to the plain-text portion of the buffer. */
  def write(s: String): Unit = {
    val _ = text.append(s)
  }

  /** Appends a single codepoint [c] to the plain-text portion of the buffer.
    * Handles supplementary codepoints (> U+FFFF) correctly via surrogate pairs.
    */
  def writeCharCode(c: Int): Unit = {
    val _ = text.appendAll(Character.toChars(c))
  }

  /** Appends [c] to the plain-text portion of the buffer. */
  def writeChar(c: Char): Unit = {
    val _ = text.append(c)
  }

  /** Appends a newline to the plain-text portion of the buffer. */
  def writeln(): Unit = {
    val _ = text.append('\n')
  }

  /** Adds an expression [expression] with its [span] to the buffer. */
  def add(expression: Expression, span: FileSpan): Unit = {
    _flushText()
    contents += expression
    spans += Nullable(span)
  }

  /** Adds another [[Interpolation]]'s contents to this buffer. */
  def addInterpolation(interpolation: Interpolation): Unit = {
    var first = true
    var i     = 0
    while (i < interpolation.contents.length) {
      val element = interpolation.contents(i)
      element match {
        case s: String =>
          val _ = text.append(s)
        case e: Expression =>
          _flushText()
          contents += e
          spans += interpolation.spans(i)
        case _ => // should not happen per Interpolation invariants
      }
      first = false
      i += 1
    }
    val _ = first
  }

  /** Builds the resulting [[Interpolation]] over [span].
    *
    * This creates a snapshot of the buffer's current state without modifying it,
    * matching the Dart behavior. The buffer can continue to be used after this call.
    */
  def interpolation(span: FileSpan): Interpolation = {
    if (text.nonEmpty) {
      new Interpolation(
        contents.toList :+ text.toString,
        spans.toList :+ Nullable.empty[FileSpan],
        span
      )
    } else {
      new Interpolation(contents.toList, spans.toList, span)
    }
  }

  private def _flushText(): Unit =
    if (text.nonEmpty) {
      contents += text.toString
      spans += Nullable.empty
      text.clear()
    }
}
