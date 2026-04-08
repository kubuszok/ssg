/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/at_rule.dart, lib/src/ast/css/modifiable/at_rule.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: at_rule.dart + modifiable/at_rule.dart -> CssAtRule.scala
 *   Convention: Dart abstract interface class -> Scala trait
 *   Idiom: childless parameter renamed from positional to named
 */
package ssg
package sass
package ast
package css

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.FileSpan
import ssg.sass.visitor.CssVisitor

/** An unknown plain CSS at-rule. */
trait CssAtRule extends CssParentNode {

  /** The name of this rule. */
  def name: CssValue[String]

  /** The value of this rule. */
  def value: Nullable[CssValue[String]]

  /** Whether the rule has no children.
    *
    * This implies `children.isEmpty`, but the reverse is not true -- for a rule like `@foo {}`, children is empty but isChildless is false.
    */
  def isChildless: Boolean
}

/** A modifiable version of CssAtRule for use in the evaluation step. */
final class ModifiableCssAtRule(
  val name:  CssValue[String],
  val span:  FileSpan,
  childless: Boolean = false,
  val value: Nullable[CssValue[String]] = Nullable.empty
) extends ModifiableCssParentNode
    with CssAtRule {

  override val isChildless: Boolean = childless

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssAtRule(this)

  def equalsIgnoringChildren(other: ModifiableCssNode): Boolean =
    other match {
      case that: ModifiableCssAtRule =>
        name == that.name && value == that.value && isChildless == that.isChildless
      case _ => false
    }

  def copyWithoutChildren(): ModifiableCssAtRule =
    ModifiableCssAtRule(name, span, childless = isChildless, value = value)

  override def addChild(child: ModifiableCssNode): Unit = {
    assert(!isChildless)
    super.addChild(child)
  }
}
