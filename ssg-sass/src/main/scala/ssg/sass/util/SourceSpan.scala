/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Source span types for error reporting and source map generation.
 * Replaces Dart's `source_span` package (FileSpan, FileLocation, SourceFile).
 */
package ssg
package sass
package util

import ssg.sass.Nullable.*

/**
 * Represents a source file that can be referenced by spans.
 *
 * @param url    optional URL identifying the source
 * @param text   the full source text
 */
final class SourceFile(
  val url: Nullable[String],
  val text: String
) {

  /** Line start offsets, computed lazily. */
  private lazy val lineStarts: Array[Int] = {
    val builder = scala.collection.mutable.ArrayBuffer[Int](0)
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i).toInt
      if (c == CharCode.$lf) {
        builder += (i + 1)
      } else if (c == CharCode.$cr) {
        if (i + 1 < text.length && text.charAt(i + 1) == CharCode.$lf) {
          i += 1
        }
        builder += (i + 1)
      }
      i += 1
    }
    builder.toArray
  }

  /** Number of lines in this file. */
  def lineCount: Int = lineStarts.length

  /** Returns the 0-based line number for the given offset. */
  def getLine(offset: Int): Int = {
    if (offset < 0 || offset > text.length) {
      throw new IndexOutOfBoundsException(s"Offset $offset out of range [0, ${text.length}]")
    }
    // Binary search for the line
    var lo = 0
    var hi = lineStarts.length - 1
    while (lo < hi) {
      val mid = lo + (hi - lo + 1) / 2
      if (lineStarts(mid) <= offset) lo = mid
      else hi = mid - 1
    }
    lo
  }

  /** Returns the 0-based column for the given offset. */
  def getColumn(offset: Int): Int = offset - lineStarts(getLine(offset))

  /** Creates a FileLocation for the given offset. */
  def location(offset: Int): FileLocation =
    FileLocation(this, offset, getLine(offset), getColumn(offset))

  /** Returns the text between two offsets. */
  def getText(start: Int, end: Int): String = text.substring(start, end)

  /** Returns the text from the given offset to the end. */
  def getText(start: Int): String = text.substring(start)

  /** Creates a span covering the given range. */
  def span(start: Int, end: Int): FileSpan =
    FileSpan(this, location(start), location(end))

  override def toString: String = url.getOrElse("<unknown>")
}

/**
 * A location within a source file.
 *
 * @param file   the source file
 * @param offset byte offset from the start of the file
 * @param line   0-based line number
 * @param column 0-based column number
 */
final case class FileLocation(
  file: SourceFile,
  offset: Int,
  line: Int,
  column: Int
) {
  def pointSpan: FileSpan = FileSpan(file, this, this)

  override def toString: String = {
    val name = file.url.getOrElse("<unknown>")
    s"$name:${line + 1}:${column + 1}"
  }
}

/**
 * A span within a source file, used for error reporting.
 *
 * @param file  the source file
 * @param start start location (inclusive)
 * @param end   end location (exclusive)
 */
final case class FileSpan(
  file: SourceFile,
  start: FileLocation,
  end: FileLocation
) {

  /** The source text covered by this span. */
  def text: String = file.getText(start.offset, end.offset)

  /** The URL of the source file, if any. */
  def sourceUrl: Nullable[String] = file.url

  /** The length of the span in characters. */
  def length: Int = end.offset - start.offset

  /** Creates a zero-width span at this span's start. */
  def pointSpan(): FileSpan = FileSpan(file, start, start)

  /** Creates a subspan within this span. */
  def subspan(startOffset: Int, endOffset: Int = -1): FileSpan = {
    val actualEnd = if (endOffset < 0) length else endOffset
    file.span(start.offset + startOffset, start.offset + actualEnd)
  }

  /** Formats an error message with source context highlighting. */
  def message(msg: String): String = {
    val loc = s"${file.url.getOrElse("<unknown>")}:${start.line + 1}:${start.column + 1}"
    s"$loc: $msg\n${highlight()}"
  }

  /** Returns highlighted source context for error display. */
  def highlight(): String = {
    val lineNum = start.line + 1
    val prefix = s"$lineNum | "
    // Get the full line text
    val lineStart = start.offset - start.column
    var lineEnd = file.text.indexOf('\n', lineStart)
    if (lineEnd < 0) lineEnd = file.text.length
    val lineText = file.text.substring(lineStart, lineEnd)
    val underline = " " * prefix.length + " " * start.column + "^" * math.max(1, length)
    s"$prefix$lineText\n$underline"
  }

  /** Creates a span that covers the range between this span and [other]. */
  def expand(other: FileSpan): FileSpan = {
    val newStart = if (start.offset <= other.start.offset) start else other.start
    val newEnd = if (end.offset >= other.end.offset) end else other.end
    FileSpan(file, newStart, newEnd)
  }

  /** Trims whitespace from both ends of the span. */
  def trim(): FileSpan = {
    val t = text
    var s = 0
    while (s < t.length && CharCode.isWhitespace(t.charAt(s).toInt)) s += 1
    var e = t.length
    while (e > s && CharCode.isWhitespace(t.charAt(e - 1).toInt)) e -= 1
    if (s == 0 && e == t.length) this
    else subspan(s, e)
  }

  /** Trims whitespace from the left of the span. */
  def trimLeft(): FileSpan = {
    val t = text
    var s = 0
    while (s < t.length && CharCode.isWhitespace(t.charAt(s).toInt)) s += 1
    if (s == 0) this else subspan(s)
  }

  /** Trims whitespace from the right of the span. */
  def trimRight(): FileSpan = {
    val t = text
    var e = t.length
    while (e > 0 && CharCode.isWhitespace(t.charAt(e - 1).toInt)) e -= 1
    if (e == t.length) this else subspan(0, e)
  }

  /** Returns the span between this span's end and [other]'s start. */
  def between(other: FileSpan): FileSpan =
    FileSpan(file, end, other.start)

  /** Returns the span from this span's start to [inner]'s start. */
  def before(inner: FileSpan): FileSpan =
    FileSpan(file, start, inner.start)

  /** Returns the span from [inner]'s end to this span's end. */
  def after(inner: FileSpan): FileSpan =
    FileSpan(file, inner.end, end)

  /** Whether this span fully contains [target]. */
  def contains(target: FileSpan): Boolean =
    start.offset <= target.start.offset && end.offset >= target.end.offset

  override def toString: String = {
    val name = file.url.getOrElse("<unknown>")
    s"FileSpan($name:${start.line + 1}:${start.column + 1}-${end.line + 1}:${end.column + 1})"
  }
}

object FileSpan {

  /** Creates a synthetic span with no real source location. */
  def synthetic(text: String): FileSpan = {
    val file = SourceFile(Nullable.Null, text)
    file.span(0, text.length)
  }
}

// ---------------------------------------------------------------------------
// Span utility extension methods (ported from dart-sass lib/src/util/span.dart)
// ---------------------------------------------------------------------------

extension (span: FileSpan) {

  /** Returns the span covering the initial identifier in this span.
    * If [includeLeading] is given, includes that many characters before the
    * identifier (e.g. 1 for `$` in variable names).
    */
  def initialIdentifier(includeLeading: Int = 0): FileSpan = {
    val t = span.text
    var i = includeLeading
    if (i < t.length && (t.charAt(i).isLetter || t.charAt(i) == '_' || t.charAt(i) == '-')) {
      i += 1
      while (i < t.length && (t.charAt(i).isLetterOrDigit || t.charAt(i) == '_' || t.charAt(i) == '-')) {
        i += 1
      }
    }
    span.subspan(0, i)
  }

  /** Returns the span with the initial `namespace.` prefix removed. */
  def withoutNamespace(): FileSpan = {
    val t = span.text
    val dotIdx = t.indexOf('.')
    if (dotIdx < 0) span
    else span.subspan(dotIdx + 1)
  }

  /** Returns the span with the initial `@rule ` prefix removed. */
  def withoutInitialAtRule(): FileSpan = {
    val t = span.text
    // Skip past @rule and any whitespace
    var i = 0
    if (i < t.length && t.charAt(i) == '@') {
      i += 1
      while (i < t.length && !Character.isWhitespace(t.charAt(i))) i += 1
      while (i < t.length && Character.isWhitespace(t.charAt(i))) i += 1
    }
    span.subspan(i)
  }

  /** Returns the span covering the first quoted string in this span. */
  def initialQuoted(): FileSpan = {
    val t = span.text
    var i = 0
    while (i < t.length && t.charAt(i) != '\'' && t.charAt(i) != '"') i += 1
    if (i >= t.length) span
    else {
      val quote = t.charAt(i)
      val start = i
      i += 1
      while (i < t.length && t.charAt(i) != quote) {
        if (t.charAt(i) == '\\') i += 1
        i += 1
      }
      if (i < t.length) i += 1 // include closing quote
      span.subspan(start, i)
    }
  }
}
