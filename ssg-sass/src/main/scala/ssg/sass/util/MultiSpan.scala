/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/multi_span.dart
 * Original: Copyright (c) 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: multi_span.dart → MultiSpan.scala
 *   Convention: Simplified — secondary spans stored as immutable Map
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/multi_span.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package util

import ssg.sass.Nullable

/** A FileSpan wrapper with secondary spans for multi-location error messages.
  */
final class MultiSpan(
  private val primary: FileSpan,
  val primaryLabel:    String,
  val secondarySpans:  Map[FileSpan, String]
) {

  def start:     FileLocation     = primary.start
  def end:       FileLocation     = primary.end
  def text:      String           = primary.text
  def file:      SourceFile       = primary.file
  def length:    Int              = primary.length
  def sourceUrl: Nullable[String] = primary.sourceUrl

  def expand(other: FileSpan): MultiSpan =
    new MultiSpan(primary.expand(other), primaryLabel, secondarySpans)

  def subspan(startOffset: Int, endOffset: Int = -1): MultiSpan =
    new MultiSpan(primary.subspan(startOffset, endOffset), primaryLabel, secondarySpans)

  /** Formats an error message including all secondary spans. */
  def message(msg: String): String = {
    val sb = new StringBuilder()
    sb.append(primary.message(msg))
    for ((span, label) <- secondarySpans) {
      sb.append("\n")
      sb.append(span.message(label))
    }
    sb.toString()
  }

  /** Highlights the primary span with secondary spans labeled. */
  def highlight(): String = {
    val sb = new StringBuilder()
    sb.append(primary.highlight())
    for ((span, label) <- secondarySpans) {
      sb.append("\n")
      sb.append(s"$label: ${span.highlight()}")
    }
    sb.toString()
  }

  override def toString: String = primary.toString
}
