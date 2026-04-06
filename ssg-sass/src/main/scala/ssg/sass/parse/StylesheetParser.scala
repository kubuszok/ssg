/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/stylesheet.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: stylesheet.dart -> StylesheetParser.scala
 *   Skeleton: dart-sass StylesheetParser is ~4100 lines and depends heavily
 *     on the evaluator/value system. Phase 6 stops at a compiling skeleton:
 *     the public surface (parse, parseExpression, parseVariableDeclaration,
 *     parseUseRule, parseSignature, parseNumber, parseParameterList) exists
 *     with the correct shape, and the internal token methods that subclasses
 *     override (styleRuleSelector, expectStatementSeparator, atEndOfStatement,
 *     lookingAtChildren, scanElse, children, statements) are abstract. The
 *     bodies will be filled in progressively alongside Phase 10 (Evaluator).
 */
package ssg
package sass
package parse

import ssg.sass.Nullable
import ssg.sass.ast.sass.{
  Expression,
  Interpolation,
  ParseTimeWarning,
  Statement,
  Stylesheet,
  UseRule,
  VariableDeclaration
}
import ssg.sass.value.SassNumber

/** The base class for both the SCSS and indented syntax parsers. */
abstract class StylesheetParser protected (
  contents: String,
  url: Nullable[String] = Nullable.Null,
  protected val parseSelectors: Boolean = false
) extends Parser(contents, url) {

  /** Warnings discovered while parsing. */
  protected val warnings: scala.collection.mutable.ListBuffer[ParseTimeWarning] =
    scala.collection.mutable.ListBuffer.empty

  /** Whether this parser emits plain CSS. Overridden by [[CssParser]]. */
  def plainCss: Boolean = false

  /** Whether this parser is the indented syntax. Overridden by [[SassParser]]. */
  def indented: Boolean

  /** The current indentation level. */
  def currentIndentation: Int

  // ---------------------------------------------------------------------------
  // Public entry points
  // ---------------------------------------------------------------------------

  /** Parses the contents as a full stylesheet. */
  def parse(): Stylesheet =
    throw new UnsupportedOperationException(
      "StylesheetParser.parse: not yet implemented in skeleton (Phase 10 dependency)"
    )

  /** Parses the contents as a single expression, returning the expression
    * and any warnings encountered.
    */
  def parseExpression(): (Expression, List[ParseTimeWarning]) =
    throw new UnsupportedOperationException(
      "StylesheetParser.parseExpression: not yet implemented in skeleton"
    )

  /** Parses the contents as a single number literal. */
  def parseNumber(): SassNumber =
    throw new UnsupportedOperationException(
      "StylesheetParser.parseNumber: not yet implemented in skeleton"
    )

  /** Parses the contents as a single variable declaration. */
  def parseVariableDeclaration(): (VariableDeclaration, List[ParseTimeWarning]) =
    throw new UnsupportedOperationException(
      "StylesheetParser.parseVariableDeclaration: not yet implemented in skeleton"
    )

  /** Parses the contents as a single `@use` rule. */
  def parseUseRule(): (UseRule, List[ParseTimeWarning]) =
    throw new UnsupportedOperationException(
      "StylesheetParser.parseUseRule: not yet implemented in skeleton"
    )

  /** Parses a function signature of the format allowed by Node Sass's
    * functions option and returns its name and parameter list.
    */
  def parseSignature(requireParens: Boolean = true): (String, Any) =
    throw new UnsupportedOperationException(
      "StylesheetParser.parseSignature: not yet implemented in skeleton"
    )

  // ---------------------------------------------------------------------------
  // Abstract hooks overridden by SCSS / Sass / CSS parsers
  // ---------------------------------------------------------------------------

  /** Consumes an interpolation for the selector portion of a style rule. */
  protected def styleRuleSelector(): Interpolation

  /** Asserts that a statement separator was consumed. */
  protected def expectStatementSeparator(name: Nullable[String] = Nullable.Null): Unit

  /** Returns whether the scanner is at the end of a statement. */
  protected def atEndOfStatement(): Boolean

  /** Returns whether the scanner is looking at the start of a child block. */
  protected def lookingAtChildren(): Boolean

  /** Consumes an `@else` clause at the given indentation. */
  protected def scanElse(ifIndentation: Int): Boolean

  /** Consumes a child block. */
  protected def children(child: () => Statement): List[Statement]

  /** Consumes a sequence of statements. */
  protected def statements(statement: () => Nullable[Statement]): List[Statement]
}
