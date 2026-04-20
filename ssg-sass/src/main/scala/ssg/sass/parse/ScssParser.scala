/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/scss.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: scss.dart -> ScssParser.scala
 *   Idiom: Minimum viable implementation providing styleRuleSelector,
 *     statement sequencing, and child block parsing.
 */
package ssg
package sass
package parse

import ssg.sass.{ Deprecation, InterpolationBuffer, Nullable }
import ssg.sass.Nullable.*
import ssg.sass.ast.sass.{ Interpolation, LoudComment, SilentComment, Statement }
import ssg.sass.util.CharCode

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** A parser for the CSS-superset SCSS syntax. */
class ScssParser(
  contents:       String,
  url:            Nullable[String] = Nullable.Null,
  parseSelectors: Boolean = false
) extends StylesheetParser(contents, url, parseSelectors) {

  override def indented:           Boolean = false
  override def currentIndentation: Int     = 0

  /** Consumes a selector interpolation until `{`.
    *
    * Basic: collects raw text as plain interpolation. Full `#{...}` expression interpolation is handled by the parent StylesheetParser.
    */
  override protected def styleRuleSelector(): Interpolation = {
    val start    = scanner.state
    val buf      = new StringBuilder()
    var brackets = 0

    boundary {
      while (!scanner.isDone) {
        val c = scanner.peekChar()
        if (c < 0) break(())
        // Detect `#{` interpolation start so we don't mistake its inner `{`/`}`
        // for selector braces. Consume the whole `#{...}` region balancing braces.
        if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          buf.append(scanner.readChar().toChar) // '#'
          buf.append(scanner.readChar().toChar) // '{'
          var depth = 1
          while (!scanner.isDone && depth > 0) {
            val cc = scanner.peekChar()
            if (cc == CharCode.$lbrace) depth += 1
            else if (cc == CharCode.$rbrace) depth -= 1
            buf.append(scanner.readChar().toChar)
          }
        } else if (brackets == 0 && c == CharCode.$lbrace) {
          break(())
        } else if (c == CharCode.$backslash) {
          // Preserve escape sequences raw (backslash + hex digits + optional
          // whitespace, or backslash + single char) so they round-trip through
          // the selector parser correctly.
          buf.append(scanner.readChar().toChar) // backslash
          if (!scanner.isDone) {
            val next = scanner.peekChar()
            if (CharCode.isHex(next)) {
              var i = 0
              while (i < 6 && !scanner.isDone && CharCode.isHex(scanner.peekChar())) {
                buf.append(scanner.readChar().toChar)
                i += 1
              }
              // Consume optional trailing whitespace and include it
              if (!scanner.isDone && CharCode.isWhitespace(scanner.peekChar()))
                buf.append(scanner.readChar().toChar)
            } else {
              buf.append(scanner.readChar().toChar)
            }
          }
        } else if (c == CharCode.$slash && scanner.peekChar(1) == CharCode.$slash) {
          // Silent comment inside selector — consume everything up to the
          // end of line. Discard from the selector text so interpolations
          // inside the comment are not evaluated as selector interpolations.
          scanner.readChar() // first '/'
          scanner.readChar() // second '/'
          while (!scanner.isDone && !CharCode.isNewline(scanner.peekChar())) {
            scanner.readChar()
          }
        } else if (c == CharCode.$slash && scanner.peekChar(1) == CharCode.$asterisk) {
          // Loud comment inside selector — consume and include in selector.
          buf.append(scanner.readChar().toChar) // '/'
          buf.append(scanner.readChar().toChar) // '*'
          while (!scanner.isDone && !(scanner.peekChar() == CharCode.$asterisk && scanner.peekChar(1) == CharCode.$slash)) {
            buf.append(scanner.readChar().toChar)
          }
          if (!scanner.isDone) {
            buf.append(scanner.readChar().toChar) // '*'
            buf.append(scanner.readChar().toChar) // '/'
          }
        } else if (c == CharCode.$double_quote || c == CharCode.$single_quote) {
          // Quoted strings — preserve verbatim including escapes.
          val q = scanner.readChar()
          buf.append(q.toChar)
          while (!scanner.isDone && scanner.peekChar() != q) {
            if (scanner.peekChar() == CharCode.$backslash) {
              buf.append(scanner.readChar().toChar) // backslash
              if (!scanner.isDone) buf.append(scanner.readChar().toChar) // next char
            } else {
              buf.append(scanner.readChar().toChar)
            }
          }
          if (!scanner.isDone) buf.append(scanner.readChar().toChar) // closing quote
        } else {
          if (c == CharCode.$lparen || c == CharCode.$lbracket) brackets += 1
          else if (c == CharCode.$rparen || c == CharCode.$rbracket) brackets -= 1
          buf.append(scanner.readChar().toChar)
        }
      }
    }

    val selectorText = buf.toString().trim
    if (selectorText.isEmpty) scanner.error("Expected selector.")
    _parseInterpolatedString(selectorText, spanFrom(start))
  }

  override protected def expectStatementSeparator(name: Nullable[String] = Nullable.Null): Unit = {
    _whitespaceWithoutComments()
    if (scanner.isDone) return
    val c = scanner.peekChar()
    if (c == CharCode.$semicolon) { scanner.readChar(); return }
    if (c == CharCode.$rbrace) return
    scanner.expectChar(CharCode.$semicolon)
  }

  override protected def atEndOfStatement(): Boolean = {
    val c = scanner.peekChar()
    c < 0 || c == CharCode.$semicolon || c == CharCode.$rbrace || c == CharCode.$lbrace
  }

  override protected def lookingAtChildren(): Boolean =
    scanner.peekChar() == CharCode.$lbrace

  override protected def scanElse(ifIndentation: Int): Boolean = {
    val start = scanner.state
    _whitespace()
    val beforeAt = scanner.state
    if (scanner.scanChar(CharCode.$at)) {
      if (scanIdentifier("else", caseSensitive = true)) true
      else if (scanIdentifier("elseif", caseSensitive = true)) {
        warnDeprecation(
          Deprecation.Elseif,
          "@elseif is deprecated and will not be supported in future Sass " +
            "versions.\n\nRecommendation: @else if",
          spanFrom(beforeAt)
        )
        scanner.position = scanner.position - 2
        true
      } else {
        scanner.state = start
        false
      }
    } else {
      scanner.state = start
      false
    }
  }

  override protected def children(child: () => Statement): List[Statement] = {
    scanner.expectChar(CharCode.$lbrace)
    _whitespaceWithoutComments()
    val stmts = mutable.ListBuffer.empty[Statement]
    boundary {
      while (true) {
        scanner.peekChar() match {
          case CharCode.`$dollar` =>
            stmts += variableDeclarationWithoutNamespace()
            // SSG convention: expectStatementSeparator already consumed `;`,
            // so skip trailing whitespace before the next token.
            _whitespaceWithoutComments()

          case CharCode.`$slash` =>
            scanner.peekChar(1) match {
              case CharCode.`$slash` =>
                stmts += _silentComment()
                _whitespaceWithoutComments()
              case CharCode.`$asterisk` =>
                stmts += _loudComment()
                _whitespaceWithoutComments()
              case _ =>
                stmts += child()
                _whitespaceWithoutComments()
            }

          case CharCode.`$semicolon` =>
            scanner.readChar()
            _whitespaceWithoutComments()

          case CharCode.`$rbrace` =>
            scanner.expectChar(CharCode.$rbrace)
            break(())

          case _ =>
            stmts += child()
            _whitespaceWithoutComments()
        }
      }
    }
    stmts.toList
  }

  override protected def statements(statement: () => Nullable[Statement]): List[Statement] = {
    val stmts = mutable.ListBuffer.empty[Statement]
    _whitespaceWithoutComments()
    while (!scanner.isDone) {
      scanner.peekChar() match {
        case CharCode.`$dollar` =>
          stmts += variableDeclarationWithoutNamespace()
          // SSG convention: expectStatementSeparator already consumed `;`,
          // so skip trailing whitespace before the next token.
          _whitespaceWithoutComments()

        case CharCode.`$slash` =>
          scanner.peekChar(1) match {
            case CharCode.`$slash` =>
              stmts += _silentComment()
              _whitespaceWithoutComments()
            case CharCode.`$asterisk` =>
              stmts += _loudComment()
              _whitespaceWithoutComments()
            case _ =>
              val child = statement()
              if (child.isDefined) stmts += child.get
              _whitespaceWithoutComments()
          }

        case CharCode.`$semicolon` =>
          scanner.readChar()
          _whitespaceWithoutComments()

        case _ =>
          val child = statement()
          if (child.isDefined) stmts += child.get
          _whitespaceWithoutComments()
      }
    }
    stmts.toList
  }

  /** Consumes a statement-level silent comment block.
    *
    * dart-sass scss.dart lines 130-151.
    */
  private def _silentComment(): SilentComment = {
    val start = scanner.state
    scanner.expect("//")

    var continue_ = true
    while (continue_) {
      while (!scanner.isDone && !CharCode.isNewline(scanner.readChar())) { /* consume */ }
      if (scanner.isDone) { continue_ = false }
      else {
        spaces()
        if (!scanner.scan("//")) { continue_ = false }
      }
    }

    if (plainCss) {
      error(
        "Silent comments aren't allowed in plain CSS.",
        spanFrom(start)
      )
    }

    val comment = new SilentComment(
      scanner.substring(start.position),
      spanFrom(start)
    )
    lastSilentComment = Nullable(comment)
    comment
  }

  /** Consumes a statement-level loud comment block.
    *
    * dart-sass scss.dart lines 154-189.
    * Handles `#{...}` interpolation, `\r`->`\n` and `\f`->`\n` normalization.
    */
  private def _loudComment(): LoudComment = {
    val start = scanner.state
    scanner.expect("/*")
    val buffer = new InterpolationBuffer()
    buffer.write("/*")
    boundary {
      while (true) {
        scanner.peekChar() match {
          case CharCode.`$hash` =>
            if (scanner.peekChar(1) == CharCode.$lbrace) {
              val (expression, span) = singleInterpolation()
              buffer.add(expression, span)
            } else {
              buffer.writeCharCode(scanner.readChar())
            }

          case CharCode.`$asterisk` =>
            buffer.writeCharCode(scanner.readChar())
            if (scanner.peekChar() == CharCode.$slash) {
              buffer.writeCharCode(scanner.readChar())
              break(())
            }

          case CharCode.`$cr` =>
            scanner.readChar()
            if (scanner.peekChar() != CharCode.$lf) buffer.writeCharCode(CharCode.$lf)

          case CharCode.`$ff` =>
            scanner.readChar()
            buffer.writeCharCode(CharCode.$lf)

          case _ =>
            buffer.writeCharCode(scanner.readChar())
        }
      }
    }
    new LoudComment(buffer.interpolation(spanFrom(start)))
  }

  /** The value of `consumeNewlines` is not relevant for this class. */
  private def _whitespace(): Unit =
    whitespace(consumeNewlines = true)

  /** The value of `consumeNewlines` is not relevant for this class. */
  private def _whitespaceWithoutComments(): Unit =
    whitespaceWithoutComments(consumeNewlines = true)
}
