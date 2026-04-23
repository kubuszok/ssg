/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/media_query.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: media_query.dart -> MediaQueryParser.scala
 *   Idiom: Faithful port of dart-sass MediaQueryParser using the inherited
 *     Parser.scanner. Supports Level 3 and Level 4 media queries: type
 *     queries, feature queries, `not`/`only` modifiers, `and`/`or`
 *     conjunctions, and parenthesized `(not ...)` conditions.
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationMap, Nullable, SassFormatException, Utils }
import ssg.sass.Nullable.*
import ssg.sass.ast.css.CssMediaQuery
import ssg.sass.util.CharCode

import scala.collection.mutable
import scala.language.implicitConversions

/** A parser for `@media` queries. */
class MediaQueryParser(
  contents:         String,
  url:              Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) extends Parser(contents, url, interpolationMap) {

  def parse(): List[CssMediaQuery] =
    wrapSpanFormatException { () =>
      val queries   = mutable.ListBuffer.empty[CssMediaQuery]
      var continue_ = true
      while (continue_) {
        _whitespace()
        queries += _mediaQuery()
        _whitespace()
        if (!scanner.scanChar(CharCode.$comma)) continue_ = false
      }
      scanner.expectDone()
      queries.toList
    }

  /** Consumes a single media query. */
  private def _mediaQuery(): CssMediaQuery = {
    // This is somewhat duplicated in StylesheetParser._mediaQuery.
    if (scanner.peekChar() == CharCode.$lparen) {
      val conditions = mutable.ListBuffer(_mediaInParens())
      _whitespace()

      var conjunction = true
      if (scanIdentifier("and")) {
        expectWhitespace()
        conditions ++= _mediaLogicSequence("and")
      } else if (scanIdentifier("or")) {
        expectWhitespace()
        conjunction = false
        conditions ++= _mediaLogicSequence("or")
      }

      return CssMediaQuery.condition(conditions.toList, conjunction = Some(conjunction))
    }

    var modifier: Option[String] = None
    var type_   : Option[String] = None
    val identifier1 = identifier()

    if (Utils.equalsIgnoreCase(identifier1, "not")) {
      expectWhitespace()
      if (!lookingAtIdentifier()) {
        // For example, "@media not (...) {"
        return CssMediaQuery.condition(List(s"(not ${_mediaInParens()})"))
      }
    }

    _whitespace()
    if (!lookingAtIdentifier()) {
      // For example, "@media screen {"
      return CssMediaQuery.type_(type_ = Some(identifier1))
    }

    val identifier2 = identifier()

    if (Utils.equalsIgnoreCase(identifier2, "and")) {
      expectWhitespace()
      // For example, "@media screen and ..."
      type_ = Some(identifier1)
    } else {
      _whitespace()
      modifier = Some(identifier1)
      type_ = Some(identifier2)
      if (scanIdentifier("and")) {
        // For example, "@media only screen and ..."
        expectWhitespace()
      } else {
        // For example, "@media only screen {"
        return CssMediaQuery.type_(type_ = type_, modifier = modifier)
      }
    }

    // We've consumed either `IDENTIFIER "and"` or
    // `IDENTIFIER IDENTIFIER "and"`.

    if (scanIdentifier("not")) {
      // For example, "@media screen and not (...) {"
      expectWhitespace()
      return CssMediaQuery.type_(
        type_ = type_,
        modifier = modifier,
        conditions = List(s"(not ${_mediaInParens()})")
      )
    }

    CssMediaQuery.type_(
      type_ = type_,
      modifier = modifier,
      conditions = _mediaLogicSequence("and")
    )
  }

  /** Consumes one or more `<media-in-parens>` expressions separated by [operator] and returns them.
    */
  private def _mediaLogicSequence(operator: String): List[String] = {
    val result    = mutable.ListBuffer.empty[String]
    var continue_ = true
    while (continue_) {
      result += _mediaInParens()
      _whitespace()

      if (!scanIdentifier(operator)) continue_ = false
      else expectWhitespace()
    }
    result.toList
  }

  /** Consumes a `<media-in-parens>` expression and returns it, parentheses included.
    */
  private def _mediaInParens(): String = {
    scanner.expectChar(CharCode.$lparen, name = "media condition in parentheses")
    val rawCond = s"(${declarationValue()})"
    scanner.expectChar(CharCode.$rparen)
    CssMediaQuery.normalizeCondition(rawCond)
  }

  /** The value of `consumeNewlines` is not relevant for this class. */
  private def _whitespace(): Unit =
    whitespace(consumeNewlines = true)
}

object MediaQueryParser {

  /** Parses [text] as a comma-separated list of media queries. */
  def parseList(text: String): List[CssMediaQuery] =
    new MediaQueryParser(text).parse()

  /** Returns the parsed list, or `None` if the text is not parseable. */
  def tryParseList(text: String): Option[List[CssMediaQuery]] =
    try Some(parseList(text))
    catch { case _: SassFormatException => None }
}
