/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/parser.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: parser.dart -> Parser.scala
 *   Convention: Dart @protected -> Scala protected
 *   Idiom: Tokenizers use boundary/break for early returns
 */
package ssg
package sass
package parse

import ssg.sass.util.{ CharCode, FileLocation, FileSpan, LazyFileSpan, LineScannerState, SpanScanner, StringScannerException }
import ssg.sass.{ InterpolationMap, MultiSpanSassFormatException, Nullable, SassFormatException, Utils }
import ssg.sass.Nullable.*

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** The abstract base class for all parsers.
  *
  * Provides utility methods and common token parsing. Unless specified otherwise, a parse method throws a [[SassFormatException]] if it fails to parse.
  */
abstract class Parser protected (
  contents:                       String,
  url:                            Nullable[String] = Nullable.Null,
  protected val interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) {

  /** The scanner that scans through the text being parsed. */
  protected val scanner: SpanScanner = new SpanScanner(contents, url)

  // ## Tokens

  /** Consumes whitespace, including any comments.
    *
    * If [consumeNewlines] is true, the indented syntax will consume newlines as whitespace. It should only be set to true in positions when a statement can't end.
    */
  protected def whitespace(consumeNewlines: Boolean): Unit = {
    whitespaceWithoutComments(consumeNewlines)
    while (scanComment()) {
      whitespaceWithoutComments(consumeNewlines)
    }
  }

  /** Consumes whitespace, but not comments.
    *
    * If [consumeNewlines] is true, the indented syntax will consume newlines as whitespace. It should only be set to true in positions when a statement can't end.
    */
  protected def whitespaceWithoutComments(consumeNewlines: Boolean): Unit =
    while (!scanner.isDone && CharCode.isWhitespace(scanner.peekChar())) {
      scanner.readChar()
    }

  /** Consumes spaces and tabs (never newlines). */
  protected def spaces(): Unit =
    while (!scanner.isDone) {
      val c = scanner.peekChar()
      if (c < 0 || !CharCode.isSpaceOrTab(c)) return
      scanner.readChar()
    }

  /** Consumes and ignores a comment if possible.
    *
    * Returns whether the comment was consumed.
    */
  protected def scanComment(): Boolean = {
    if (scanner.peekChar() != CharCode.$slash) return false
    scanner.peekChar(1) match {
      case CharCode.`$slash` =>
        silentComment()
      case CharCode.`$asterisk` =>
        loudComment()
        true
      case _ => false
    }
  }

  /** Consumes and ignores a single silent (Sass-style) comment, not including the trailing newline.
    *
    * Returns whether the comment was consumed.
    */
  protected def silentComment(): Boolean = {
    scanner.expect("//")
    while (!scanner.isDone && !CharCode.isNewline(scanner.peekChar())) {
      scanner.readChar()
    }
    true
  }

  /** Consumes and ignores a loud (CSS-style) comment.
    *
    * In dart-sass, `scanner.readChar()` throws at EOF, so an
    * unterminated comment naturally produces "expected more input."
    * Our `readChar()` returns -1 instead, so we check explicitly.
    */
  protected def loudComment(): Unit = {
    scanner.expect("/*")
    while (true) {
      if (scanner.isDone) scanner.error("expected more input.")
      var next = scanner.readChar()
      if (next != CharCode.$asterisk) { /* continue */ }
      else {
        // consume consecutive asterisks
        if (scanner.isDone) scanner.error("expected more input.")
        next = scanner.readChar()
        while (next == CharCode.$asterisk) {
          if (scanner.isDone) scanner.error("expected more input.")
          next = scanner.readChar()
        }
        if (next == CharCode.$slash) return
      }
    }
  }

  /** Like [[whitespace]], but throws an error if no whitespace is consumed.
    *
    * If [consumeNewlines] is true, the indented syntax will consume newlines as whitespace. It should only be set to true in positions when a statement can't end.
    */
  protected def expectWhitespace(consumeNewlines: Boolean = false): Unit = {
    if (scanner.isDone || !(CharCode.isWhitespace(scanner.peekChar()) || scanComment())) {
      scanner.error("Expected whitespace.")
    }
    whitespace(consumeNewlines)
  }

  /** Consumes a plain CSS identifier and returns it.
    *
    * If [normalize] is true, converts underscores into hyphens. If [unit] is true, doesn't parse a `-` followed by a digit.
    */
  protected def identifier(normalize: Boolean = false, unit: Boolean = false): String = {
    val buf = new StringBuilder()

    if (scanner.scanChar(CharCode.$minus)) {
      buf.append('-')
      if (scanner.scanChar(CharCode.$minus)) {
        buf.append('-')
        identifierBody(buf, normalize, unit)
        return buf.toString()
      }
    }

    val c = scanner.peekChar()
    if (c < 0) {
      scanner.error("Expected identifier.")
    } else if (c == CharCode.$underscore && normalize) {
      scanner.readChar()
      buf.append('-')
    } else if (CharCode.isNameStart(c)) {
      buf.append(scanner.readChar().toChar)
    } else if (c == CharCode.$backslash) {
      buf.append(escape(identifierStart = true))
    } else {
      scanner.error("Expected identifier.")
    }

    identifierBody(buf, normalize, unit)
    buf.toString()
  }

  /** Consumes a chunk of a plain CSS identifier after the name start. */
  protected def identifierBody(): String = {
    val buf = new StringBuilder()
    identifierBody(buf, normalize = false, unit = false)
    if (buf.isEmpty) scanner.error("Expected identifier body.")
    buf.toString()
  }

  /** Parses the identifier body into the given buffer. */
  private def identifierBody(buf: StringBuilder, normalize: Boolean, unit: Boolean): Unit =
    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c < 0) break(())
        else if (unit && c == CharCode.$minus) {
          // Disallow `-` followed by a dot or a digit in units.
          val next = scanner.peekChar(1)
          if (next == CharCode.$dot || (next >= 0 && CharCode.isDigit(next))) break(())
          buf.append(scanner.readChar().toChar)
        } else if (normalize && c == CharCode.$underscore) {
          scanner.readChar()
          buf.append('-')
        } else if (CharCode.isName(c)) {
          buf.append(scanner.readChar().toChar)
        } else if (c == CharCode.$backslash) {
          buf.append(escape())
        } else {
          break(())
        }
      }
    }

  /** Consumes an escape sequence and returns the text that defines it.
    *
    * Port of dart-sass `escape`. If the decoded codepoint is a valid CSS
    * name character (or nameStart when `identifierStart` is true), the
    * decoded character is returned directly. Control characters
    * (U+0000–U+001F, U+007F) and leading digits are re-encoded as
    * `\hex ` with a trailing space. All other non-name codepoints are
    * returned as `\<char>`.
    */
  protected def escape(identifierStart: Boolean = false): String = {
    scanner.expectChar(CharCode.$backslash)
    val first = scanner.peekChar()
    if (first < 0) scanner.error("Expected escape sequence.")

    var value = 0
    if (CharCode.isNewline(first)) {
      scanner.error("Expected escape sequence.")
    } else if (CharCode.isHex(first)) {
      var i = 0
      while (i < 6 && CharCode.isHex(scanner.peekChar())) {
        value = (value << 4) | CharCode.asHex(scanner.readChar())
        i += 1
      }
      scanCharIf(c => c >= 0 && CharCode.isWhitespace(c))

      if (value == 0 || (value >= 0xd800 && value <= 0xdfff) || value > 0x10ffff) {
        value = 0xfffd
      }
    } else {
      value = scanner.readChar()
    }

    if (if (identifierStart) CharCode.isNameStart(value) else CharCode.isName(value)) {
      new String(Character.toChars(value))
    } else if (value <= 0x1f || value == 0x7f || (identifierStart && CharCode.isDigit(value))) {
      // Re-encode control characters as \hex with trailing space
      val sb = new StringBuilder()
      sb.append('\\')
      if (value > 0xf) sb.append(Character.forDigit(value >> 4, 16))
      sb.append(Character.forDigit(value & 0xf, 16))
      sb.append(' ')
      sb.toString()
    } else {
      "\\" + new String(Character.toChars(value))
    }
  }

  /** Returns whether the scanner is looking at an identifier. */
  protected def lookingAtIdentifier(forward: Int = 0): Boolean = {
    val first = scanner.peekChar(forward)
    if (first < 0) return false
    if (CharCode.isNameStart(first) || first == CharCode.$backslash) return true
    if (first != CharCode.$minus) return false

    val second = scanner.peekChar(forward + 1)
    if (second < 0) return false
    CharCode.isNameStart(second) || second == CharCode.$backslash || second == CharCode.$minus
  }

  /** Returns whether the scanner is immediately before a sequence of characters that could be part of a plain CSS identifier body. */
  protected def lookingAtIdentifierBody(): Boolean = {
    val c = scanner.peekChar()
    c >= 0 && (CharCode.isName(c) || c == CharCode.$backslash)
  }

  /** Returns whether the scanner is immediately before a number.
    *
    * This follows [[https://drafts.csswg.org/css-syntax-3/#starts-with-a-number the CSS algorithm]].
    */
  protected def lookingAtNumber(): Boolean = {
    val first = scanner.peekChar()
    if (first >= 0 && CharCode.isDigit(first)) return true
    if (first == CharCode.$dot) {
      val second = scanner.peekChar(1)
      return second >= 0 && CharCode.isDigit(second)
    }
    if (first == CharCode.$plus || first == CharCode.$minus) {
      val second = scanner.peekChar(1)
      if (second >= 0 && CharCode.isDigit(second)) return true
      if (second == CharCode.$dot) {
        val third = scanner.peekChar(2)
        return third >= 0 && CharCode.isDigit(third)
      }
    }
    false
  }

  /** Consumes an identifier if its name exactly matches [text]. */
  protected def scanIdentifier(text: String, caseSensitive: Boolean = false): Boolean = {
    if (!lookingAtIdentifier()) return false
    val start = scanner.state
    if (_consumeIdentifier(text, caseSensitive) && !lookingAtIdentifierBody()) {
      true
    } else {
      scanner.state = start
      false
    }
  }

  /** Returns whether an identifier whose name exactly matches [text] is at the current scanner position.
    *
    * This doesn't move the scan pointer forward.
    */
  protected def matchesIdentifier(text: String, caseSensitive: Boolean = false): Boolean = {
    if (!lookingAtIdentifier()) return false
    val start  = scanner.state
    val result = _consumeIdentifier(text, caseSensitive) && !lookingAtIdentifierBody()
    scanner.state = start
    result
  }

  /** Consumes [text] as an identifier, but doesn't verify whether there's additional identifier text afterwards.
    *
    * Returns true if the full [text] is consumed and false otherwise, but doesn't reset the scan pointer.
    */
  private def _consumeIdentifier(text: String, caseSensitive: Boolean): Boolean = {
    var i = 0
    while (i < text.length) {
      if (!scanIdentChar(text.charAt(i).toInt, caseSensitive = caseSensitive)) return false
      i += 1
    }
    true
  }

  /** Consumes an identifier and asserts that its name exactly matches [text]. */
  protected def expectIdentifier(text: String, name: Nullable[String] = Nullable.Null, caseSensitive: Boolean = false): Unit = {
    val label = name.getOrElse(s"\"$text\"")
    val start = scanner.position
    var i     = 0
    while (i < text.length) {
      if (!scanIdentChar(text.charAt(i).toInt, caseSensitive = caseSensitive)) {
        scanner.error(s"Expected $label.", start, 0)
      }
      i += 1
    }
    if (lookingAtIdentifierBody()) {
      scanner.error(s"Expected $label", start, 0)
    }
  }

  /** Consumes a quoted CSS string and returns its contents. */
  protected def string(): String = {
    val quote = scanner.readChar()
    if (quote != CharCode.$single_quote && quote != CharCode.$double_quote) {
      scanner.error("Expected string.", scanner.position - 1, 0)
    }

    val buf = new StringBuilder()
    boundary {
      while (true) {
        val next = scanner.peekChar()
        if (next == quote) {
          scanner.readChar()
          break(())
        } else if (next < 0 || CharCode.isNewline(next)) {
          scanner.error(s"Expected ${quote.toChar}.")
        } else if (next == CharCode.$backslash) {
          val ahead = scanner.peekChar(1)
          if (ahead >= 0 && CharCode.isNewline(ahead)) {
            scanner.readChar()
            scanner.readChar()
          } else {
            buf.append(new String(Character.toChars(escapeCharacter())))
          }
        } else {
          buf.append(scanner.readChar().toChar)
        }
      }
    }
    buf.toString()
  }

  /** Consumes an escape sequence and returns the codepoint it represents. */
  protected def escapeCharacter(): Int = {
    scanner.expectChar(CharCode.$backslash)
    val first = scanner.peekChar()
    if (first < 0) scanner.error("Expected escape sequence.")

    if (CharCode.isNewline(first)) scanner.error("Expected escape sequence.")
    if (CharCode.isHex(first)) {
      var value = 0
      var i     = 0
      while (i < 6 && CharCode.isHex(scanner.peekChar())) {
        value = (value << 4) | CharCode.asHex(scanner.readChar())
        i += 1
      }
      val peek = scanner.peekChar()
      if (peek >= 0 && CharCode.isWhitespace(peek)) scanner.readChar()
      if (value == 0 || (value >= 0xd800 && value <= 0xdfff) || value > 0x10ffff) 0xfffd
      else value
    } else {
      scanner.readChar()
    }
  }

  // ## Characters

  // Consumes the next character if it matches [condition].
  //
  // Returns whether or not the character was consumed.
  protected def scanCharIf(condition: Int => Boolean): Boolean = {
    val next = scanner.peekChar()
    if (!condition(next)) return false
    scanner.readChar()
    true
  }

  /** Consumes the next character or escape sequence if it matches [expected].
    *
    * Matching will be case-insensitive unless [caseSensitive] is true.
    */
  protected def scanIdentChar(char: Int, caseSensitive: Boolean = false): Boolean = {
    def matches(actual: Int): Boolean =
      if (caseSensitive) actual == char
      else CharCode.characterEqualsIgnoreCase(char, actual)

    val next = scanner.peekChar()
    if (next >= 0 && next != CharCode.$backslash && matches(next)) {
      scanner.readChar()
      true
    } else if (next == CharCode.$backslash) {
      val start = scanner.state
      if (matches(escapeCharacter())) true
      else {
        scanner.state = start
        false
      }
    } else {
      false
    }
  }

  /** Consumes the next character or escape sequence and asserts it matches [char].
    *
    * Matching will be case-insensitive unless [caseSensitive] is true.
    */
  protected def expectIdentChar(letter: Int, caseSensitive: Boolean = false): Unit = {
    if (scanIdentChar(letter, caseSensitive = caseSensitive)) return
    scanner.error(s"""Expected "${letter.toChar}".""", scanner.position, 0)
  }

  // ## Utilities

  /** Runs [consumer] and returns the source text that it consumes.
    * dart-sass: `rawText` (parser.dart:664-668).
    */
  protected def rawText(consumer: () => Unit): String = {
    val start = scanner.position
    consumer()
    scanner.substring(start)
  }

  /** Consumes and returns a natural number as a double. */
  protected def naturalNumber(): Double = {
    val first = scanner.readChar()
    if (first < 0 || !CharCode.isDigit(first)) {
      scanner.error("Expected digit.", scanner.position - 1, 0)
    }
    var number = CharCode.asDecimal(first).toDouble
    while (!scanner.isDone && CharCode.isDigit(scanner.peekChar()))
      number = number * 10 + CharCode.asDecimal(scanner.readChar())
    number
  }

  /** Consumes tokens until it reaches a top-level `;`, `)`, `]`, or `}` and returns the collected text.
    */
  protected def declarationValue(allowEmpty: Boolean = false): String = {
    val buf          = new StringBuilder()
    val brackets     = scala.collection.mutable.ArrayBuffer.empty[Int]
    var wroteNewline = false

    boundary {
      while (true) {
        val next = scanner.peekChar()
        if (next < 0) break(())
        else if (next == CharCode.$backslash) {
          buf.append(escape(identifierStart = true))
          wroteNewline = false
        } else if (next == CharCode.$double_quote || next == CharCode.$single_quote) {
          val start = scanner.position
          string()
          buf.append(scanner.substring(start))
          wroteNewline = false
        } else if (next == CharCode.$slash) {
          val p1 = scanner.peekChar(1)
          if (p1 == CharCode.$asterisk) {
            val start = scanner.position
            loudComment()
            buf.append(scanner.substring(start))
            wroteNewline = false
          } else if (p1 == CharCode.$slash) {
            // Silent comment — consume to end of line without buffering.
            // dart-sass skips silent comments inside declaration values.
            while (!scanner.isDone && !CharCode.isNewline(scanner.peekChar()))
              scanner.readChar()
          } else {
            buf.append(scanner.readChar().toChar)
            wroteNewline = false
          }
        } else if (next == CharCode.$space || next == CharCode.$tab) {
          val peek1 = scanner.peekChar(1)
          if (wroteNewline || peek1 < 0 || !CharCode.isWhitespace(peek1)) {
            buf.append(' ')
          }
          scanner.readChar()
        } else if (next == CharCode.$lf || next == CharCode.$cr || next == CharCode.$ff) {
          // Preceding char check
          val prev = if (scanner.position > 0) scanner.string.charAt(scanner.position - 1).toInt else -1
          if (prev < 0 || !CharCode.isNewline(prev)) buf.append('\n')
          scanner.readChar()
          wroteNewline = true
        } else if (next == CharCode.$lparen || next == CharCode.$lbrace || next == CharCode.$lbracket) {
          buf.append(next.toChar)
          brackets += CharCode.opposite(scanner.readChar())
          wroteNewline = false
        } else if (next == CharCode.$rparen || next == CharCode.$rbrace || next == CharCode.$rbracket) {
          if (brackets.isEmpty) break(())
          buf.append(next.toChar)
          val expected = brackets.remove(brackets.length - 1)
          scanner.expectChar(expected)
          wroteNewline = false
        } else if (next == CharCode.$semicolon) {
          if (brackets.isEmpty) break(())
          buf.append(scanner.readChar().toChar)
        } else if (next == CharCode.$u || next == CharCode.$U) {
          val url = tryUrl()
          if (url.isDefined) {
            buf.append(url.get)
          } else {
            buf.append(scanner.readChar().toChar)
          }
          wroteNewline = false
        } else {
          if (lookingAtIdentifier()) {
            buf.append(identifier())
          } else {
            buf.append(scanner.readChar().toChar)
          }
          wroteNewline = false
        }
      }
    }

    if (brackets.nonEmpty) scanner.expectChar(brackets.last)
    if (!allowEmpty && buf.isEmpty) scanner.error("Expected token.")
    buf.toString()
  }

  /** Consumes a `url()` token if possible, and returns null otherwise. */
  protected def tryUrl(): Nullable[String] = boundary[Nullable[String]] {
    // NOTE: this logic is largely duplicated in ScssParser._tryUrlContents.
    // Most changes here should be mirrored there.

    val start = scanner.state
    if (!scanIdentifier("url")) break(Nullable.Null)

    if (!scanner.scanChar(CharCode.$lparen)) {
      scanner.state = start
      break(Nullable.Null)
    }

    whitespace(consumeNewlines = true)

    // Match Ruby Sass's behavior: parse a raw URL() if possible, and if not
    // backtrack and re-parse as a function expression.
    val buffer = new StringBuilder()
    buffer.append("url(")
    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c < 0) {
          break(())
        } else if (c == CharCode.$backslash) {
          buffer.append(escape())
        } else if (c == CharCode.$percent || c == CharCode.$ampersand || c == CharCode.$hash ||
                   (c >= CharCode.$asterisk && c <= CharCode.$tilde) ||
                   c >= 0x80) {
          buffer.append(scanner.readChar().toChar)
        } else if (CharCode.isWhitespace(c)) {
          whitespace(consumeNewlines = true)
          if (scanner.peekChar() != CharCode.$rparen) {
            break(())
          }
        } else if (c == CharCode.$rparen) {
          buffer.append(scanner.readChar().toChar)
          break(Nullable(buffer.toString()))
        } else {
          break(())
        }
      }
    }

    scanner.state = start
    Nullable.Null
  }

  /** Consumes a Sass variable name, returning the name without the dollar sign. */
  protected def variableName(): String = {
    scanner.expectChar(CharCode.$dollar)
    identifier(normalize = true)
  }

  /** Like [[scanner.spanFrom]], but passes the span through [[interpolationMap]] if it's available. */
  protected def spanFrom(start: LineScannerState): FileSpan = {
    val span = scanner.spanFrom(start)
    if (interpolationMap.isEmpty) span
    else new LazyFileSpan(() => interpolationMap.get.mapSpan(span)).span
  }

  /** Like [[spanFrom(start)]] but with an explicit end state. */
  protected def spanFrom(start: LineScannerState, end: LineScannerState): FileSpan = {
    val span = scanner.spanFrom(start, end)
    if (interpolationMap.isEmpty) span
    else new LazyFileSpan(() => interpolationMap.get.mapSpan(span)).span
  }

  /** Like [[scanner.spanFromPosition]], but passes the span through [[interpolationMap]] if it's available. */
  protected def spanFromPosition(start: Int, end: Int = -1): FileSpan = {
    val span = scanner.spanFromPosition(start, end)
    if (interpolationMap.isEmpty) span
    else new LazyFileSpan(() => interpolationMap.get.mapSpan(span)).span
  }

  /** Throws a SassFormatException with the given message and span. */
  protected def error(message: String, span: FileSpan): Nothing =
    throw new SassFormatException(message, span)

  /** Runs callback and, if it throws a [[StringScannerException]], rethrows it with [message] as its message. */
  protected def withErrorMessage[T](message: String)(callback: => T): T =
    try callback
    catch {
      case error: StringScannerException =>
        throw new StringScannerException(message, error.span)
    }

  /** Runs [callback] and wraps any [[StringScannerException]] it throws in a [[SassFormatException]]. */
  protected def wrapSpanFormatException[T](callback: () => T): T = {
    try {
      try callback()
      catch {
        case error: StringScannerException if interpolationMap.isDefined =>
          val mapped = interpolationMap.get.mapSpan(error.span)
          if (mapped eq error.span) throw error
          throw new StringScannerException(error.getMessage, mapped)
      }
    } catch {
      case error: MultiSpanSassFormatException =>
        // MultiSpanSassFormatException is-a SassFormatException; catch it first
        var span           = error.span
        var secondarySpans = error.secondarySpans
        if (Utils.startsWithIgnoreCase(error.sassMessage, "expected")) {
          span = _adjustExceptionSpan(span)
          secondarySpans = secondarySpans.map { case (s, desc) => _adjustExceptionSpan(s) -> desc }
        }
        throw new MultiSpanSassFormatException(error.sassMessage, span, error.primaryLabel, secondarySpans)
      case error: SassFormatException =>
        var span = error.span
        if (Utils.startsWithIgnoreCase(error.sassMessage, "expected")) {
          span = _adjustExceptionSpan(span)
        }
        throw new SassFormatException(error.sassMessage, span)
      case error: StringScannerException =>
        var span = error.span
        if (Utils.startsWithIgnoreCase(error.getMessage, "expected")) {
          span = _adjustExceptionSpan(span)
        }
        throw new SassFormatException(error.getMessage, span)
    }
  }

  /** Moves span to [[_firstNewlineBefore]] if necessary. */
  private def _adjustExceptionSpan(span: FileSpan): FileSpan = {
    if (span.length > 0) return span
    val start = _firstNewlineBefore(span.start)
    if (start == span.start) span else start.pointSpan
  }

  /** If [location] is separated from the previous non-whitespace character in `scanner.string` by one or more newlines, returns the location of the last separating newline.
    *
    * Otherwise returns [location].
    *
    * This helps avoid missing token errors pointing at the next closing bracket rather than the line where the problem actually occurred.
    */
  private def _firstNewlineBefore(location: FileLocation): FileLocation = {
    val text            = location.file.getText(0, location.offset)
    var index           = location.offset - 1
    var lastNewline: Int = -1
    while (index >= 0) {
      val codeUnit = text.charAt(index).toInt
      if (!CharCode.isWhitespace(codeUnit)) {
        return if (lastNewline < 0) location
        else location.file.location(lastNewline)
      }
      if (CharCode.isNewline(codeUnit)) lastNewline = index
      index -= 1
    }

    // If the document *only* contains whitespace before [location], fall
    // through to the original [location].
    location
  }
}

object Parser {

  /** Concrete parser subclass used only for static entry-point utilities. */
  final private class StaticParser(text: String) extends Parser(text, Nullable.Null)

  /** Parses [text] as a CSS identifier and returns the result. */
  def parseIdentifier(text: String): String = {
    val p = new StaticParser(text)
    p.wrapSpanFormatException { () =>
      val result = p.identifier()
      p.scanner.expectDone()
      result
    }
  }

  /** Returns whether [text] is a valid CSS identifier. */
  def isIdentifier(text: String): Boolean =
    try {
      parseIdentifier(text)
      true
    } catch {
      case _: SassFormatException => false
    }

  /** Returns whether [text] starts like a variable declaration. */
  def isVariableDeclarationLike(text: String): Boolean = {
    val p = new StaticParser(text)
    try {
      if (!p.scanner.scanChar(CharCode.$dollar)) return false
      if (!p.lookingAtIdentifier()) return false
      p.identifier()
      p.whitespace(consumeNewlines = true)
      p.scanner.scanChar(CharCode.$colon)
    } catch {
      case _: SassFormatException | _: StringScannerException => false
    }
  }
}
