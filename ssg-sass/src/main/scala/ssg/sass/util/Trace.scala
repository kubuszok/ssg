/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Lightweight Sass stack trace for error reporting.
 * Replaces Dart's `stack_trace` package (Trace, Frame).
 */
package ssg
package sass
package util

import ssg.sass.Nullable.*

/**
 * A single frame in a Sass stack trace.
 *
 * @param url    the source URL
 * @param line   1-based line number
 * @param column 1-based column number (or -1 if unknown)
 * @param member the member name (mixin, function, or rule)
 */
final case class Frame(
  url: String,
  line: Int,
  column: Int = -1,
  member: Nullable[String] = Nullable.Null
) {
  override def toString: String = {
    val loc = if (column >= 0) s"$url:$line:$column" else s"$url:$line"
    member.fold(loc)(m => s"$loc in $m")
  }
}

object Frame {

  /** Creates a Frame from a FileSpan and optional member name. */
  def fromSpan(span: FileSpan, member: Nullable[String] = Nullable.Null): Frame = {
    Frame(
      url = span.file.url.getOrElse("<unknown>"),
      line = span.start.line + 1,
      column = span.start.column + 1,
      member = member
    )
  }
}

/**
 * A Sass stack trace, consisting of a list of frames.
 *
 * @param frames the stack frames, from innermost to outermost
 */
final case class Trace(frames: List[Frame]) {

  override def toString: String = {
    if (frames.isEmpty) ""
    else {
      val sb = new StringBuilder()
      for (frame <- frames) {
        sb.append(frame.toString)
        sb.append('\n')
      }
      sb.toString()
    }
  }
}

object Trace {
  val empty: Trace = Trace(Nil)
}
