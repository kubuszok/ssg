/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/media_rule.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: media_rule.dart + modifiable/media_rule.dart -> CssMediaRule.scala
 *   Convention: Dart abstract interface class -> Scala trait
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/ast/css/media_rule.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package ast
package css

import ssg.sass.util.FileSpan
import ssg.sass.visitor.CssVisitor

/** A plain CSS `@media` rule. */
trait CssMediaRule extends CssParentNode {

  /** The queries for this rule.
    *
    * This is never empty.
    */
  def queries: List[CssMediaQuery]
}

/** A modifiable version of CssMediaRule for use in the evaluation step. */
final class ModifiableCssMediaRule(
  queriesIterable: Iterable[CssMediaQuery],
  val span:        FileSpan
) extends ModifiableCssParentNode
    with CssMediaRule {

  val queries: List[CssMediaQuery] = {
    val qs = queriesIterable.toList
    require(qs.nonEmpty, "queries may not be empty.")
    qs
  }

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssMediaRule(this)

  def equalsIgnoringChildren(other: ModifiableCssNode): Boolean =
    other match {
      case that: ModifiableCssMediaRule => queries == that.queries
      case _ => false
    }

  def copyWithoutChildren(): ModifiableCssMediaRule =
    ModifiableCssMediaRule(queries, span)
}
