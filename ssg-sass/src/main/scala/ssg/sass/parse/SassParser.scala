/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/sass.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: sass.dart -> SassParser.scala
 *   Skeleton: inherits from StylesheetParser for the indented syntax.
 */
package ssg
package sass
package parse

import ssg.sass.Nullable
import ssg.sass.ast.sass.{Interpolation, Statement}

/** A parser for the whitespace-sensitive indented Sass syntax. */
class SassParser(
  contents: String,
  url: Nullable[String] = Nullable.Null,
  parseSelectors: Boolean = false
) extends StylesheetParser(contents, url, parseSelectors) {

  override def indented: Boolean = true
  override def currentIndentation: Int = 0

  override protected def styleRuleSelector(): Interpolation =
    throw new UnsupportedOperationException("SassParser.styleRuleSelector: not yet implemented in skeleton")

  override protected def expectStatementSeparator(name: Nullable[String] = Nullable.Null): Unit =
    throw new UnsupportedOperationException("SassParser.expectStatementSeparator: not yet implemented in skeleton")

  override protected def atEndOfStatement(): Boolean =
    throw new UnsupportedOperationException("SassParser.atEndOfStatement: not yet implemented in skeleton")

  override protected def lookingAtChildren(): Boolean =
    throw new UnsupportedOperationException("SassParser.lookingAtChildren: not yet implemented in skeleton")

  override protected def scanElse(ifIndentation: Int): Boolean =
    throw new UnsupportedOperationException("SassParser.scanElse: not yet implemented in skeleton")

  override protected def children(child: () => Statement): List[Statement] =
    throw new UnsupportedOperationException("SassParser.children: not yet implemented in skeleton")

  override protected def statements(statement: () => Nullable[Statement]): List[Statement] =
    throw new UnsupportedOperationException("SassParser.statements: not yet implemented in skeleton")
}
