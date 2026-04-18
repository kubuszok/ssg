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

/** Represents a source file that can be referenced by spans.
  *
  * @param url
  *   optional URL identifying the source
  * @param text
  *   the full source text
  */
final class SourceFile(
  val url:  Nullable[String],
  val text: String
) {

  /** Line start offsets, computed lazily. */
  private lazy val lineStarts: Array[Int] = {
    val builder = scala.collection.mutable.ArrayBuffer[Int](0)
    var i       = 0
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

/** A location within a source file.
  *
  * @param file
  *   the source file
  * @param offset
  *   byte offset from the start of the file
  * @param line
  *   0-based line number
  * @param column
  *   0-based column number
  */
final case class FileLocation(
  file:   SourceFile,
  offset: Int,
  line:   Int,
  column: Int
) {
  def pointSpan: FileSpan = FileSpan(file, this, this)

  override def toString: String = {
    val name = file.url.getOrElse("<unknown>")
    s"$name:${line + 1}:${column + 1}"
  }
}

/** A span within a source file, used for error reporting.
  *
  * @param file
  *   the source file
  * @param start
  *   start location (inclusive)
  * @param end
  *   end location (exclusive)
  */
final case class FileSpan(
  file:  SourceFile,
  start: FileLocation,
  end:   FileLocation
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
    val prefix  = s"$lineNum | "
    // Get the full line text
    val lineStart = start.offset - start.column
    var lineEnd   = file.text.indexOf('\n', lineStart)
    if (lineEnd < 0) lineEnd = file.text.length
    val lineText  = file.text.substring(lineStart, lineEnd)
    val underline = " " * prefix.length + " " * start.column + "^" * math.max(1, length)
    s"$prefix$lineText\n$underline"
  }

  /** Creates a span that covers the range between this span and [other]. */
  def expand(other: FileSpan): FileSpan = {
    val newStart = if (start.offset <= other.start.offset) start else other.start
    val newEnd   = if (end.offset >= other.end.offset) end else other.end
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

  /** Returns a span covering the text after this span and before [other].
    *
    * Throws an [IllegalArgumentException] if [other.start] isn't on or after `this.end` in the same file.
    */
  def between(other: FileSpan): FileSpan = {
    if (sourceUrl != other.sourceUrl) {
      throw new IllegalArgumentException(s"$this and $other are in different files.")
    } else if (end.offset > other.start.offset) {
      throw new IllegalArgumentException(s"$this isn't before $other.")
    }
    file.span(end.offset, other.start.offset)
  }

  /** Returns a span covering the text from the beginning of this span to the beginning of [inner].
    *
    * Throws an [IllegalArgumentException] if [inner] isn't fully within this span.
    */
  def before(inner: FileSpan): FileSpan = {
    if (sourceUrl != inner.sourceUrl) {
      throw new IllegalArgumentException(s"$this and $inner are in different files.")
    } else if (inner.start.offset < start.offset || inner.end.offset > end.offset) {
      throw new IllegalArgumentException(s"$inner isn't inside $this.")
    }
    file.span(start.offset, inner.start.offset)
  }

  /** Returns a span covering the text from the end of [inner] to the end of this span.
    *
    * Throws an [IllegalArgumentException] if [inner] isn't fully within this span.
    */
  def after(inner: FileSpan): FileSpan = {
    if (sourceUrl != inner.sourceUrl) {
      throw new IllegalArgumentException(s"$this and $inner are in different files.")
    } else if (inner.start.offset < start.offset || inner.end.offset > end.offset) {
      throw new IllegalArgumentException(s"$inner isn't inside $this.")
    }
    file.span(inner.end.offset, end.offset)
  }

  /** Whether this [FileSpan] contains the [target] FileSpan.
    *
    * Validates the FileSpans to be in the same file and for the [target] to be within this [FileSpan]'s inclusive range
    * `[start,end]`.
    */
  def contains(target: FileSpan): Boolean =
    file.url == target.file.url &&
      start.offset <= target.start.offset &&
      end.offset >= target.end.offset

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

  /** Returns the span of the identifier at the start of this span.
    *
    * If [includeLeading] is greater than 0, that many additional characters will be included from the start of this
    * span before looking for an identifier.
    */
  def initialIdentifier(includeLeading: Int = 0): FileSpan = {
    val t = span.text
    var i = includeLeading
    i = _scanIdentifier(t, i)
    span.subspan(0, i)
  }

  /** Returns a subspan excluding the identifier at the start of this span. */
  def withoutInitialIdentifier(): FileSpan = {
    val t = span.text
    val i = _scanIdentifier(t, 0)
    span.subspan(i)
  }

  /** Returns a subspan excluding a namespace and `.` at the start of this span. */
  def withoutNamespace(): FileSpan =
    span.withoutInitialIdentifier().subspan(1)

  /** Returns a subspan excluding an initial at-rule and any whitespace after it. */
  def withoutInitialAtRule(): FileSpan = {
    val t = span.text
    // Skip past '@'
    var i = 0
    if (i < t.length && t.charAt(i).toInt == CharCode.$at) {
      i += 1
    }
    // Scan past the identifier (e.g. "import", "use", "include")
    i = _scanIdentifier(t, i)
    span.subspan(i).trimLeft()
  }

  /** Returns the span of the quoted text at the start of this span.
    *
    * This span must start with `"` or `'`.
    */
  def initialQuoted(): FileSpan = {
    val t     = span.text
    val quote = t.charAt(0)
    var i     = 1
    while (true) {
      val next = t.charAt(i)
      i += 1
      if (next == quote) return span.subspan(0, i)
      if (next == '\\') i += 1 // skip escaped character
    }
    span.subspan(0, i) // unreachable but needed for compiler
  }
}

/** Consumes an identifier from [text] starting at position [pos].
  *
  * Returns the position after the identifier.
  */
private def _scanIdentifier(text: String, pos: Int): Int = {
  import scala.util.boundary
  import scala.util.boundary.break
  var i = pos
  boundary[Int] {
    while (i < text.length) {
      val c = text.charAt(i).toInt
      if (c == CharCode.$backslash) {
        // Consume the backslash-escape sequence
        i += 1 // skip backslash
        if (i < text.length) {
          val next = text.charAt(i).toInt
          if (CharCode.isHex(next)) {
            // Hex escape: consume up to 6 hex digits + optional trailing whitespace
            var count = 0
            while (i < text.length && count < 6 && CharCode.isHex(text.charAt(i).toInt)) {
              i += 1
              count += 1
            }
            // consume optional trailing whitespace
            if (i < text.length && CharCode.isWhitespace(text.charAt(i).toInt)) {
              i += 1
            }
          } else {
            // Non-hex escape: just consume the next character
            i += 1
          }
        }
      } else if (CharCode.isName(c)) {
        i += 1
      } else {
        break(i)
      }
    }
    i
  }
}
