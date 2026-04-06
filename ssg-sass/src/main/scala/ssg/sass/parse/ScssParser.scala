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

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.ast.sass.{Interpolation, Statement}
import ssg.sass.util.CharCode

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** A parser for the CSS-superset SCSS syntax. */
class ScssParser(
  contents: String,
  url: Nullable[String] = Nullable.Null,
  parseSelectors: Boolean = false
) extends StylesheetParser(contents, url, parseSelectors) {

  override def indented: Boolean = false
  override def currentIndentation: Int = 0

  /** Consumes a selector interpolation until `{`.
    *
    * Simplified: collects raw text as plain interpolation. Proper interpolation
    * parsing (with `#{...}` expressions) is a TODO.
    */
  override protected def styleRuleSelector(): Interpolation = {
    val start = scanner.state
    val buf = new StringBuilder()
    var brackets = 0

    boundary {
      while (!scanner.isDone) {
        val c = scanner.peekChar()
        if (c < 0) break(())
        if (brackets == 0 && c == CharCode.$lbrace) break(())
        if (c == CharCode.$lparen || c == CharCode.$lbracket) brackets += 1
        else if (c == CharCode.$rparen || c == CharCode.$rbracket) brackets -= 1
        buf.append(scanner.readChar().toChar)
      }
    }

    val selectorText = buf.toString().trim
    if (selectorText.isEmpty) scanner.error("Expected selector.")
    Interpolation.plain(selectorText, spanFrom(start))
  }

  override protected def expectStatementSeparator(name: Nullable[String] = Nullable.Null): Unit = {
    whitespace(consumeNewlines = false)
    if (scanner.isDone) return
    val c = scanner.peekChar()
    if (c == CharCode.$semicolon) scanner.readChar()
    else if (c == CharCode.$rbrace) () // close of block implies statement end
    else {
      val label = name.fold("statement")(n => s"$n statement")
      scanner.error(s"Expected ';' after $label.")
    }
  }

  override protected def atEndOfStatement(): Boolean = {
    val c = scanner.peekChar()
    c < 0 || c == CharCode.$semicolon || c == CharCode.$rbrace
  }

  override protected def lookingAtChildren(): Boolean =
    scanner.peekChar() == CharCode.$lbrace

  override protected def scanElse(ifIndentation: Int): Boolean = {
    whitespace(consumeNewlines = true)
    val start = scanner.state
    if (scanner.scanChar(CharCode.$at)) {
      if (scanIdentifier("else")) true
      else {
        scanner.state = start
        false
      }
    } else false
  }

  override protected def children(child: () => Statement): List[Statement] = {
    scanner.expectChar(CharCode.$lbrace)
    whitespace(consumeNewlines = true)
    val stmts = mutable.ListBuffer.empty[Statement]
    while (!scanner.isDone && scanner.peekChar() != CharCode.$rbrace) {
      stmts += child()
      whitespace(consumeNewlines = true)
    }
    scanner.expectChar(CharCode.$rbrace)
    stmts.toList
  }

  override protected def statements(statement: () => Nullable[Statement]): List[Statement] = {
    val stmts = mutable.ListBuffer.empty[Statement]
    whitespace(consumeNewlines = true)
    while (!scanner.isDone) {
      val s = statement()
      if (s.isDefined) stmts += s.get
      whitespace(consumeNewlines = true)
    }
    stmts.toList
  }
}
