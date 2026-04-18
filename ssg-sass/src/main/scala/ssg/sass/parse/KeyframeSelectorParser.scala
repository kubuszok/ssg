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
 *   Idiom: Faithful port of dart-sass KeyframeSelectorParser using the
 *     inherited Parser.scanner. Preserves `from`/`to` as-is (no normalization),
 *     supports scientific notation (e/E exponents), and `+` prefix for
 *     percentages.
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationMap, Nullable, SassFormatException }
import ssg.sass.Nullable.*
import ssg.sass.util.CharCode

import scala.collection.mutable
import scala.language.implicitConversions

/** A parser for `@keyframes` block selectors. */
class KeyframeSelectorParser(
  contents:         String,
  url:              Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) extends Parser(contents, url, interpolationMap) {

  def parse(): List[String] = {
    wrapSpanFormatException { () =>
      val selectors = mutable.ListBuffer.empty[String]
      var continue_ = true
      while (continue_) {
        _whitespace()
        if (lookingAtIdentifier()) {
          if (scanIdentifier("from")) {
            selectors += "from"
          } else {
            expectIdentifier("to", name = "\"to\" or \"from\"")
            selectors += "to"
          }
        } else {
          selectors += _percentage()
        }
        _whitespace()
        if (!scanner.scanChar(CharCode.$comma)) continue_ = false
      }
      scanner.expectDone()

      selectors.toList
    }
  }

  private def _percentage(): String = {
    val buffer = new StringBuilder()
    if (scanner.scanChar(CharCode.$plus)) buffer.appendAll(Character.toChars(CharCode.$plus))

    val second = scanner.peekChar()
    if (!CharCode.isDigit(second) && second != CharCode.$dot) {
      scanner.error("Expected number.")
    }

    while (CharCode.isDigit(scanner.peekChar())) {
      buffer.appendAll(Character.toChars(scanner.readChar()))
    }

    if (scanner.peekChar() == CharCode.$dot) {
      buffer.appendAll(Character.toChars(scanner.readChar()))

      while (CharCode.isDigit(scanner.peekChar())) {
        buffer.appendAll(Character.toChars(scanner.readChar()))
      }
    }

    if (scanIdentChar(CharCode.$e)) {
      buffer.appendAll(Character.toChars(CharCode.$e))
      scanner.peekChar() match {
        case CharCode.`$plus` | CharCode.`$minus` =>
          buffer.appendAll(Character.toChars(scanner.readChar()))
        case _ => ()
      }
      if (!CharCode.isDigit(scanner.peekChar())) scanner.error("Expected digit.")

      var hasDigit = true
      while (hasDigit) {
        buffer.appendAll(Character.toChars(scanner.readChar()))
        if (!CharCode.isDigit(scanner.peekChar())) hasDigit = false
      }
    }

    scanner.expectChar(CharCode.$percent)
    buffer.appendAll(Character.toChars(CharCode.$percent))
    buffer.toString()
  }

  /** The value of `consumeNewlines` is not relevant for this class. */
  private def _whitespace(): Unit =
    whitespace(consumeNewlines = true)
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
