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
 *   Skeleton: inherits from StylesheetParser; overrides are stubs.
 */
package ssg
package sass
package parse

import ssg.sass.Nullable
import ssg.sass.ast.sass.{Interpolation, Statement}

/** A parser for the CSS-superset SCSS syntax. */
class ScssParser(
  contents: String,
  url: Nullable[String] = Nullable.Null,
  parseSelectors: Boolean = false
) extends StylesheetParser(contents, url, parseSelectors) {

  override def indented: Boolean = false
  override def currentIndentation: Int = 0

  override protected def styleRuleSelector(): Interpolation =
    throw new UnsupportedOperationException("ScssParser.styleRuleSelector: not yet implemented in skeleton")

  override protected def expectStatementSeparator(name: Nullable[String] = Nullable.Null): Unit =
    throw new UnsupportedOperationException("ScssParser.expectStatementSeparator: not yet implemented in skeleton")

  override protected def atEndOfStatement(): Boolean =
    throw new UnsupportedOperationException("ScssParser.atEndOfStatement: not yet implemented in skeleton")

  override protected def lookingAtChildren(): Boolean =
    throw new UnsupportedOperationException("ScssParser.lookingAtChildren: not yet implemented in skeleton")

  override protected def scanElse(ifIndentation: Int): Boolean =
    throw new UnsupportedOperationException("ScssParser.scanElse: not yet implemented in skeleton")

  override protected def children(child: () => Statement): List[Statement] =
    throw new UnsupportedOperationException("ScssParser.children: not yet implemented in skeleton")

  override protected def statements(statement: () => Nullable[Statement]): List[Statement] =
    throw new UnsupportedOperationException("ScssParser.statements: not yet implemented in skeleton")
}
