/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/supports_rule.dart, lib/src/ast/css/modifiable/supports_rule.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: supports_rule.dart + modifiable/supports_rule.dart -> CssSupportsRule.scala
 *   Convention: Dart abstract interface class -> Scala trait
 */
package ssg
package sass
package ast
package css

import ssg.sass.util.FileSpan
import ssg.sass.visitor.CssVisitor

/** A plain CSS `@supports` rule. */
trait CssSupportsRule extends CssParentNode {

  /** The supports condition. */
  def condition: CssValue[String]
}

/** A modifiable version of CssSupportsRule for use in the evaluation step. */
final class ModifiableCssSupportsRule(
  val condition: CssValue[String],
  val span: FileSpan
) extends ModifiableCssParentNode with CssSupportsRule {

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssSupportsRule(this)

  def equalsIgnoringChildren(other: ModifiableCssNode): Boolean =
    other match {
      case that: ModifiableCssSupportsRule => condition == that.condition
      case _ => false
    }

  def copyWithoutChildren(): ModifiableCssSupportsRule =
    ModifiableCssSupportsRule(condition, span)
}
