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
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationMap, Nullable, SassFormatException }
import ssg.sass.ast.sass.AtRootQuery
import ssg.sass.util.FileSpan

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
      val name = readIdentifier()
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

  private def readIdentifier(): String = {
    val start    = pos
    var continue = true
    while (continue && pos < text.length) {
      val c = text.charAt(pos)
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
        pos += 1
      } else continue = false
    }
    text.substring(start, pos)
  }

  private def skipWs(): Unit = {
    var continue = true
    while (continue && pos < text.length) {
      val c = text.charAt(pos)
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos += 1
      else continue = false
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
