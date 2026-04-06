/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/keyframe_selector.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: keyframe_selector.dart -> KeyframeSelectorParser.scala
 *   Idiom: Phase 11 — small text-based parser. Normalizes `from`->`0%` and
 *          `to`->`100%`, and validates that percentages fall in [0, 100].
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationMap, Nullable, SassFormatException }
import ssg.sass.util.FileSpan

/** A parser for `@keyframes` block selectors. */
class KeyframeSelectorParser(
  contents:         String,
  url:              Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) extends Parser(contents, url, interpolationMap) {

  private val text: String = contents
  private var pos:  Int    = 0

  private def fail(msg: String): Nothing =
    throw new SassFormatException(msg, FileSpan.synthetic(text))

  def parse(): List[String] = {
    pos = 0
    skipWs()
    val out = scala.collection.mutable.ListBuffer.empty[String]
    out += parseOne()
    skipWs()
    while (pos < text.length && text.charAt(pos) == ',') {
      pos += 1
      skipWs()
      out += parseOne()
      skipWs()
    }
    if (pos < text.length) fail(s"Unexpected token in keyframe selector: '${text.substring(pos)}'")
    out.toList
  }

  private def parseOne(): String = {
    skipWs()
    if (pos >= text.length) fail("Expected keyframe selector.")
    val c = text.charAt(pos)
    if ((c >= '0' && c <= '9') || c == '.') {
      parsePercentage()
    } else if (c == 'f' || c == 'F' || c == 't' || c == 'T') {
      val ident = readIdentifier().toLowerCase
      ident match {
        case "from" => "0%"
        case "to"   => "100%"
        case other  => fail(s"Expected 'from', 'to', or a percentage; got '$other'.")
      }
    } else {
      fail(s"Expected keyframe selector at position $pos.")
    }
  }

  private def parsePercentage(): String = {
    val start = pos
    while (pos < text.length && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos += 1
    if (pos < text.length && text.charAt(pos) == '.') {
      pos += 1
      while (pos < text.length && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos += 1
    }
    if (pos >= text.length || text.charAt(pos) != '%') {
      fail(s"Expected '%' in keyframe percentage at position $pos.")
    }
    val numText = text.substring(start, pos)
    pos += 1
    val value =
      try numText.toDouble
      catch { case _: NumberFormatException => fail(s"Invalid percentage '$numText'.") }
    if (value < 0.0 || value > 100.0) {
      fail(s"Keyframe percentage must be between 0 and 100, got $value.")
    }
    s"$numText%"
  }

  private def readIdentifier(): String = {
    val start    = pos
    var continue = true
    while (continue && pos < text.length) {
      val c = text.charAt(pos)
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == '_') pos += 1
      else continue = false
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

object KeyframeSelectorParser {

  /** Parses [text] as a comma-separated list of keyframe selectors. */
  def parseList(text: String): List[String] =
    new KeyframeSelectorParser(text).parse()

  /** Returns the parsed list, or `None` if the text is not parseable. */
  def tryParseList(text: String): Option[List[String]] =
    try Some(parseList(text))
    catch { case _: SassFormatException => None }
}
