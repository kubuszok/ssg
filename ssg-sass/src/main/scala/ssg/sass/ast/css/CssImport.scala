/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/import.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: import.dart + modifiable/import.dart -> CssImport.scala
 *   Convention: Dart abstract interface class -> Scala trait
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/ast/css/import.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package ast
package css

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.FileSpan
import ssg.sass.visitor.CssVisitor

/** A plain CSS `@import`. */
trait CssImport extends CssNode {

  /** The URL being imported.
    *
    * This includes quotes.
    */
  def url: CssValue[String]

  /** The modifiers (such as media or supports queries) attached to this import. */
  def modifiers: Nullable[CssValue[String]]
}

/** A modifiable version of CssImport for use in the evaluation step. */
final class ModifiableCssImport(
  val url:       CssValue[String],
  val span:      FileSpan,
  val modifiers: Nullable[CssValue[String]] = Nullable.empty
) extends ModifiableCssNode
    with CssImport {

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssImport(this)
}
