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
 *   Idiom: Phase 11 — implements a small recursive-descent parser over the
 *          raw query text. Sufficient for the common Level 3 syntax: type
 *          queries (`screen`), feature queries (`(max-width: 600px)`), the
 *          `not`/`only` modifiers, `and`-conjoined feature lists, and a
 *          comma-separated list of queries.
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationMap, Nullable, SassFormatException }
import ssg.sass.ast.css.CssMediaQuery
import ssg.sass.util.FileSpan

/** A parser for `@media` queries. */
class MediaQueryParser(
  contents:         String,
  url:              Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) extends Parser(contents, url, interpolationMap) {

  // We deliberately bypass the Parser scanner here and operate on the raw
  // text — media query syntax is small and isolated, and the standard scanner
  // would force us to thread spans we don't need for the structured AST.
  private val text: String = contents
  private var pos:  Int    = 0

  private def errorSpan:         FileSpan = FileSpan.synthetic(text)
  private def fail(msg: String): Nothing  =
    throw new SassFormatException(msg, errorSpan)

  def parse(): List[CssMediaQuery] = {
    pos = 0
    skipWs()
    val queries = scala.collection.mutable.ListBuffer.empty[CssMediaQuery]
    queries += parseSingle()
    skipWs()
    while (pos < text.length && text.charAt(pos) == ',') {
      pos += 1
      skipWs()
      queries += parseSingle()
      skipWs()
    }
    if (pos < text.length) {
      fail(s"Unexpected token in media query: '${text.substring(pos)}'")
    }
    queries.toList
  }

  /** Parses a single media query (no commas). */
  private def parseSingle(): CssMediaQuery = {
    skipWs()
    if (pos >= text.length) fail("Expected media query.")

    // (cond) and (cond) ... — a condition-only query.
    if (text.charAt(pos) == '(') {
      val first = parseCondition()
      skipWs()
      val rest = parseAndChain()
      return CssMediaQuery.condition(first :: rest, conjunction = Some(true))
    }

    // Otherwise an identifier — possibly preceded by `not` or `only`.
    val ident = readIdentifier()
    val lower = ident.toLowerCase
    if (lower == "not" || lower == "only") {
      skipWs()
      val typeName = readIdentifier()
      skipWs()
      val conds = if (peekKeyword("and")) {
        consumeKeyword("and")
        skipWs()
        parseAndConditions()
      } else Nil
      CssMediaQuery.type_(
        type_ = Some(typeName),
        modifier = Some(ident),
        conditions = conds
      )
    } else {
      // Bare type, optionally followed by `and (cond) and (cond) ...`
      skipWs()
      val conds = if (peekKeyword("and")) {
        consumeKeyword("and")
        skipWs()
        parseAndConditions()
      } else Nil
      CssMediaQuery.type_(type_ = Some(ident), modifier = None, conditions = conds)
    }
  }

  /** Parses a chain of `and (cond)` segments after an initial condition. */
  private def parseAndChain(): List[String] = {
    val out = scala.collection.mutable.ListBuffer.empty[String]
    skipWs()
    while (peekKeyword("and")) {
      consumeKeyword("and")
      skipWs()
      out += parseCondition()
      skipWs()
    }
    out.toList
  }

  /** Parses one or more `(cond) and (cond) ...` items. */
  private def parseAndConditions(): List[String] = {
    val out = scala.collection.mutable.ListBuffer.empty[String]
    out += parseCondition()
    skipWs()
    while (peekKeyword("and")) {
      consumeKeyword("and")
      skipWs()
      out += parseCondition()
      skipWs()
    }
    out.toList
  }

  /** Parses a single parenthesized condition, returning it including the surrounding parentheses (matching how dart-sass stores them).
    */
  private def parseCondition(): String = {
    if (pos >= text.length || text.charAt(pos) != '(') {
      fail(s"Expected '(' in media query at position $pos.")
    }
    val start = pos
    var depth = 0
    var done  = false
    while (!done && pos < text.length) {
      val c = text.charAt(pos)
      if (c == '(') depth += 1
      else if (c == ')') {
        depth -= 1
        if (depth == 0) {
          pos += 1
          done = true
        }
      }
      if (!done) pos += 1
    }
    if (!done) fail("Unclosed media query condition.")
    text.substring(start, pos)
  }

  private def readIdentifier(): String = {
    val start    = pos
    var continue = true
    while (continue && pos < text.length) {
      val c = text.charAt(pos)
      if (c == '-' || c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        pos += 1
      } else continue = false
    }
    if (start == pos) fail(s"Expected identifier in media query at position $pos.")
    text.substring(start, pos)
  }

  private def peekKeyword(kw: String): Boolean = {
    val end = pos + kw.length
    if (end > text.length) false
    else {
      var i  = 0
      var ok = true
      while (ok && i < kw.length) {
        val a = Character.toLowerCase(text.charAt(pos + i))
        val b = Character.toLowerCase(kw.charAt(i))
        if (a != b) ok = false
        i += 1
      }
      if (!ok) false
      else if (end == text.length) true
      else {
        val c = text.charAt(end)
        c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '('
      }
    }
  }

  private def consumeKeyword(kw: String): Unit = {
    if (!peekKeyword(kw)) fail(s"Expected '$kw' in media query.")
    pos += kw.length
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

object MediaQueryParser {

  /** Parses [text] as a comma-separated list of media queries. */
  def parseList(text: String): List[CssMediaQuery] =
    new MediaQueryParser(text).parse()

  /** Returns the parsed list, or `None` if the text is not parseable. */
  def tryParseList(text: String): Option[List[CssMediaQuery]] =
    try Some(parseList(text))
    catch { case _: SassFormatException => None }
}
