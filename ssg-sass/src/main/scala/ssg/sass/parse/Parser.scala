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

import ssg.sass.util.{ CharCode, FileSpan, LineScannerState, SpanScanner, StringScannerException }
import ssg.sass.{ InterpolationMap, Nullable, SassFormatException }
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
    var continue = true
    while (continue) {
      whitespaceWithoutComments(consumeNewlines)
      continue = scanComment()
    }
  }

  /** Consumes whitespace, but not comments. */
  protected def whitespaceWithoutComments(consumeNewlines: Boolean): Unit =
    while (!scanner.isDone) {
      val c = scanner.peekChar()
      if (c < 0) return
      if (consumeNewlines) {
        if (CharCode.isWhitespace(c)) scanner.readChar()
        else return
      } else {
        if (CharCode.isSpaceOrTab(c)) scanner.readChar()
        else return
      }
    }

  /** Consumes spaces and tabs (never newlines). */
  protected def spaces(): Unit =
    while (!scanner.isDone) {
      val c = scanner.peekChar()
      if (c < 0 || !CharCode.isSpaceOrTab(c)) return
      scanner.readChar()
    }

  /** Consumes and ignores a comment if possible. Returns true if consumed. */
  protected def scanComment(): Boolean = {
    if (scanner.peekChar() != CharCode.$slash) return false
    scanner.peekChar(1) match {
      case CharCode.`$slash` =>
        silentComment()
        true
      case CharCode.`$asterisk` =>
        loudComment()
        true
      case _ => false
    }
  }

  /** Consumes and ignores a single silent (Sass-style) comment. */
  protected def silentComment(): Unit = {
    scanner.expect("//")
    while (!scanner.isDone) {
      val c = scanner.peekChar()
      if (c < 0 || CharCode.isNewline(c)) return
      scanner.readChar()
    }
  }

  /** Consumes and ignores a loud (CSS-style) comment. */
  protected def loudComment(): Unit = {
    scanner.expect("/*")
    var done = false
    while (!done) {
      var next = scanner.readChar()
      if (next == CharCode.$asterisk) {
        while (next == CharCode.$asterisk)
          next = scanner.readChar()
        if (next == CharCode.$slash) done = true
      }
    }
  }

  /** Consumes whitespace and errors if none was found. */
  protected def expectWhitespace(consumeNewlines: Boolean = false): Unit = {
    if (scanner.isDone) scanner.error("Expected whitespace.")
    val c     = scanner.peekChar()
    val hasWs = if (consumeNewlines) CharCode.isWhitespace(c) else CharCode.isSpaceOrTab(c)
    if (!hasWs && !scanComment()) {
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
      val peek = scanner.peekChar()
      if (peek >= 0 && CharCode.isWhitespace(peek)) scanner.readChar()

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

  /** Returns whether the scanner is looking at the given identifier text. */
  protected def lookingAtIdentifierBody(): Boolean = {
    val c = scanner.peekChar()
    c >= 0 && (CharCode.isName(c) || c == CharCode.$backslash)
  }

  /** Consumes an identifier and returns whether it matches [text]. */
  protected def scanIdentifier(text: String, caseSensitive: Boolean = false): Boolean = {
    if (!lookingAtIdentifier()) return false
    val start   = scanner.state
    var i       = 0
    var matched = true
    while (matched && i < text.length) {
      val expected = text.charAt(i).toInt
      val actual   = scanner.peekChar()
      val match_   =
        if (caseSensitive) actual == expected
        else CharCode.characterEqualsIgnoreCase(actual, expected)
      if (match_) {
        scanner.readChar()
        i += 1
      } else {
        matched = false
      }
    }
    if (matched && !lookingAtIdentifierBody()) {
      true
    } else {
      scanner.state = start
      false
    }
  }

  /** Consumes an identifier and throws if it doesn't match [text]. */
  protected def expectIdentifier(text: String, name: Nullable[String] = Nullable.Null): Unit =
    if (!scanIdentifier(text)) {
      val label = name.getOrElse(s"\"$text\"")
      scanner.error(s"Expected $label.")
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

  /** Consumes a Sass variable name, returning the name without the dollar sign. */
  protected def variableName(): String = {
    scanner.expectChar(CharCode.$dollar)
    identifier(normalize = true)
  }

  /** Creates a span from the given start state to the current position. */
  protected def spanFrom(start: LineScannerState): FileSpan = scanner.spanFrom(start)

  /** Throws a SassFormatException with the given message and span. */
  protected def error(message: String, span: FileSpan): Nothing =
    throw new SassFormatException(message, span)

  /** Wraps [body] in a handler that rethrows scanner errors as [[SassFormatException]]. */
  protected def wrapSpanFormatException[T](body: () => T): T =
    try body()
    catch {
      case e: StringScannerException =>
        throw new SassFormatException(e.getMessage, e.span)
    }
}

object Parser {

  /** A minimal concrete parser for static entry points. */
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
