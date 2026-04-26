/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/at_root_query.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: at_root_query.dart -> AtRootQueryParser.scala
 *   Idiom: Phase 11 — small text-based parser. Accepts the standard
 *          `(with: name name)` and `(without: name name)` forms.
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/parse/at_root_query.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationMap, Nullable, SassFormatException }
import ssg.sass.ast.sass.AtRootQuery
import ssg.sass.util.FileSpan

import scala.util.boundary
import scala.util.boundary.break

/** A parser for `@at-root` queries. */
class AtRootQueryParser(
  contents:         String,
  url:              Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) extends Parser(contents, url, interpolationMap) {

  private val text: String = contents
  private var pos:  Int    = 0

  private def fail(msg: String): Nothing =
    throw new SassFormatException(msg, FileSpan.synthetic(text))

  def parse(): AtRootQuery = {
    pos = 0
    skipWs()
    if (pos >= text.length || text.charAt(pos) != '(') {
      fail("Expected '(' at start of @at-root query.")
    }
    pos += 1
    skipWs()
    val keyword = readIdentifier().toLowerCase
    val include = keyword match {
      case "with"    => true
      case "without" => false
      case _         => fail(s"Expected 'with' or 'without' in @at-root query, got '$keyword'.")
    }
    skipWs()
    if (pos >= text.length || text.charAt(pos) != ':') {
      fail("Expected ':' after '" + keyword + "' in @at-root query.")
    }
    pos += 1
    skipWs()
    val names = scala.collection.mutable.LinkedHashSet.empty[String]
    while (pos < text.length && text.charAt(pos) != ')') {
      val name = readNameOrString()
      if (name.isEmpty) fail("Expected a name in @at-root query.")
      names += name.toLowerCase
      skipWs()
    }
    if (pos >= text.length || text.charAt(pos) != ')') {
      fail("Expected ')' to close @at-root query.")
    }
    pos += 1
    skipWs()
    if (pos < text.length) fail(s"Unexpected token after @at-root query: '${text.substring(pos)}'")
    new AtRootQuery(names.toSet, include)
  }

  /** Reads either an unquoted identifier or a quoted string value.
    *
    * Dart-sass parses the @at-root query values as full Sass expressions, so `(without: "media")` resolves to `media` (quotes stripped). Our parser captures raw text, so we need to handle quoted
    * values explicitly here.
    */
  private def readNameOrString(): String = {
    if (pos < text.length) {
      val c = text.charAt(pos)
      if (c == '"' || c == '\'') {
        return readQuotedString(c)
      }
    }
    readIdentifier()
  }

  private def readQuotedString(quote: Char): String = {
    pos += 1 // skip opening quote
    val buf = new StringBuilder()
    boundary {
      while (pos < text.length) {
        val c = text.charAt(pos)
        if (c == quote) {
          pos += 1 // skip closing quote
          break(())
        } else if (c == '\\' && pos + 1 < text.length) {
          pos += 1
          buf.append(text.charAt(pos))
          pos += 1
        } else {
          buf.append(c)
          pos += 1
        }
      }
    }
    buf.toString()
  }

  private def readIdentifier(): String = {
    val start = pos
    boundary {
      while (pos < text.length) {
        val c = text.charAt(pos)
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
          pos += 1
        } else break(())
      }
    }
    text.substring(start, pos)
  }

  private def skipWs(): Unit =
    boundary {
      while (pos < text.length) {
        val c = text.charAt(pos)
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos += 1
        else break(())
      }
    }
}

object AtRootQueryParser {

  /** Parses [text] as an `@at-root` query. */
  def parseQuery(text: String): AtRootQuery =
    new AtRootQueryParser(text).parse()

  /** Returns the parsed query, or `None` if the text is not parseable. */
  def tryParseQuery(text: String): Option[AtRootQuery] =
    try Some(parseQuery(text))
    catch { case _: SassFormatException => None }
}
