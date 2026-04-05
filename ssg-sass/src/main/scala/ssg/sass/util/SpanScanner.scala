/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * String scanner with source span tracking for Sass parsing.
 * Replaces Dart's `string_scanner` package (StringScanner, SpanScanner, LineScannerState).
 */
package ssg
package sass
package util

import ssg.sass.Nullable.*

import scala.language.implicitConversions
import scala.util.matching.Regex

/**
 * Saved scanner state for backtracking and span creation.
 *
 * @param position character offset in the source
 * @param line     0-based line number
 * @param column   0-based column number
 */
final case class LineScannerState(
  position: Int,
  line: Int,
  column: Int
)

/**
 * A string scanner that tracks source positions for span generation.
 *
 * This is the foundation for all Sass parsing. It wraps a source string and
 * provides character-by-character and regex-based scanning with source span
 * creation for error reporting.
 *
 * @param string    the source text to scan
 * @param sourceUrl optional source URL for error messages
 */
final class SpanScanner(
  val string: String,
  sourceUrl: Nullable[String] = Nullable.Null
) {

  /** The underlying source file for span creation. */
  val sourceFile: SourceFile = SourceFile(sourceUrl, string)

  /** Current scan position (0-based character offset). */
  var position: Int = 0

  /** Current 0-based line number. */
  private var _line: Int = 0

  /** Current 0-based column number. */
  private var _column: Int = 0

  /** The last regex match result, if any. */
  private var _lastMatch: Nullable[scala.util.matching.Regex.Match] = Nullable.Null

  // --- State management ---

  /** Returns the current scanner state for backtracking. */
  def state: LineScannerState = LineScannerState(position, _line, _column)

  /** Restores the scanner to a previously saved state. */
  def state_=(s: LineScannerState): Unit = {
    position = s.position
    _line = s.line
    _column = s.column
  }

  /** The last successful regex match, or null if none. */
  def lastMatch: Nullable[scala.util.matching.Regex.Match] = _lastMatch

  /** Whether the scanner has reached the end of the input. */
  def isDone: Boolean = position >= string.length

  /** The remaining unscanned text. */
  def rest: String = string.substring(position)

  /** The length of the remaining text. */
  def restLength: Int = string.length - position

  // --- Character operations ---

  /** Peeks at the character at [offset] positions ahead of the current position.
    * Returns -1 if the position is past the end of the input.
    */
  def peekChar(offset: Int = 0): Int = {
    val index = position + offset
    if (index < 0 || index >= string.length) -1
    else string.charAt(index).toInt
  }

  /** Reads and consumes the next character. Returns -1 if at end. */
  def readChar(): Int = {
    if (isDone) -1
    else {
      val c = string.charAt(position).toInt
      _advance(c)
      c
    }
  }

  /** Consumes the next character if it matches [expected]. Returns true if consumed. */
  def scanChar(expected: Int): Boolean = {
    if (position < string.length && string.charAt(position).toInt == expected) {
      _advance(expected)
      true
    } else {
      false
    }
  }

  /** Consumes the next character, asserting it matches [expected]. */
  def expectChar(expected: Int, name: Nullable[String] = Nullable.Null): Unit = {
    if (position >= string.length || string.charAt(position).toInt != expected) {
      val label = name.getOrElse(s"'${expected.toChar}'")
      _error(s"Expected $label.")
    }
    _advance(expected)
  }

  // --- String/regex operations ---

  /** Tries to match [pattern] at the current position. Returns true and advances if matched. */
  def scan(pattern: String): Boolean = {
    if (string.startsWith(pattern, position)) {
      var i = 0
      while (i < pattern.length) {
        _advance(pattern.charAt(i).toInt)
        i += 1
      }
      _lastMatch = Nullable.Null
      true
    } else {
      false
    }
  }

  /** Tries to match [regex] at the current position. Returns true and advances if matched. */
  def scan(regex: Regex): Boolean = {
    regex.findPrefixMatchOf(string.subSequence(position, string.length)) match {
      case Some(m) =>
        _lastMatch = m
        var i = 0
        while (i < m.matched.length) {
          _advance(m.matched.charAt(i).toInt)
          i += 1
        }
        true
      case scala.None =>
        _lastMatch = Nullable.Null
        false
    }
  }

  /** Asserts that [pattern] matches at the current position and advances. */
  def expect(pattern: String, name: Nullable[String] = Nullable.Null): Unit = {
    if (!scan(pattern)) {
      val label = name.getOrElse(s"\"$pattern\"")
      _error(s"Expected $label.")
    }
  }

  /** Asserts that [regex] matches at the current position and advances. */
  def expect(regex: Regex, name: String): Unit = {
    if (!scan(regex)) {
      _error(s"Expected $name.")
    }
  }

  /** Asserts that the scanner is at the end of the input. */
  def expectDone(): Unit = {
    if (!isDone) {
      _error("Expected end of input.")
    }
  }

  /** Returns a substring of the source from [start] to [end] (or current position). */
  def substring(start: Int, end: Int = -1): String = {
    val actualEnd = if (end < 0) position else end
    string.substring(start, actualEnd)
  }

  // --- Span creation ---

  /** Creates a span from [start] to the current position. */
  def spanFrom(start: LineScannerState): FileSpan = {
    val startLoc = FileLocation(sourceFile, start.position, start.line, start.column)
    val endLoc = FileLocation(sourceFile, position, _line, _column)
    FileSpan(sourceFile, startLoc, endLoc)
  }

  /** Creates a span between two saved states. */
  def spanFrom(start: LineScannerState, end: LineScannerState): FileSpan = {
    val startLoc = FileLocation(sourceFile, start.position, start.line, start.column)
    val endLoc = FileLocation(sourceFile, end.position, end.line, end.column)
    FileSpan(sourceFile, startLoc, endLoc)
  }

  /** Creates a zero-length span at the current position. */
  def emptySpan: FileSpan = {
    val loc = FileLocation(sourceFile, position, _line, _column)
    FileSpan(sourceFile, loc, loc)
  }

  /** Creates a span from integer positions. */
  def spanFromPosition(start: Int, end: Int = -1): FileSpan = {
    val actualEnd = if (end < 0) position else end
    sourceFile.span(start, actualEnd)
  }

  // --- Error reporting ---

  /** Throws a FormatException with span information. */
  def error(message: String, position: Int = -1, length: Int = 0): Nothing = {
    val pos = if (position < 0) this.position else position
    val len = if (length <= 0) 0 else length
    val span = sourceFile.span(pos, pos + len)
    throw new StringScannerException(message, span)
  }

  private def _error(message: String): Nothing = {
    error(message, position, 0)
  }

  // --- Internal helpers ---

  /** Advances past a character, updating line/column tracking. */
  private def _advance(c: Int): Unit = {
    position += 1
    if (c == CharCode.$lf) {
      _line += 1
      _column = 0
    } else if (c == CharCode.$cr) {
      // CR not followed by LF counts as a newline
      if (position >= string.length || string.charAt(position) != '\n') {
        _line += 1
        _column = 0
      } else {
        _column += 1
      }
    } else {
      _column += 1
    }
  }
}

/** Exception thrown by SpanScanner when scanning fails. */
final class StringScannerException(
  msg: String,
  val span: FileSpan
) extends RuntimeException(span.message(msg))
