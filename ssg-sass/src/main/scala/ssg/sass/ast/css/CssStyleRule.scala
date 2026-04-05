/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/style_rule.dart, lib/src/ast/css/modifiable/style_rule.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: style_rule.dart + modifiable/style_rule.dart -> CssStyleRule.scala
 *   Convention: SelectorList is a placeholder type (Any) until selector AST is ported
 *   Idiom: Box[SelectorList] used for mutable selector reference from extension store
 */
package ssg
package sass
package ast
package css

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.{Box, FileSpan}
import ssg.sass.visitor.CssVisitor

/** A plain CSS style rule.
  *
  * This applies style declarations to elements that match a given selector.
  * Note that this isn't strictly plain CSS, since the selector may still
  * contain placeholder selectors.
  *
  * SelectorList is represented as Any until the selector AST is ported.
  */
trait CssStyleRule extends CssParentNode {

  /** The selector for this rule. */
  def selector: Any /* SelectorList */

  /** The selector for this rule, before any extensions were applied. */
  def originalSelector: Any /* SelectorList */

  /** Whether this style rule was originally defined in a plain CSS stylesheet. */
  def fromPlainCss: Boolean
}

/** A modifiable version of CssStyleRule for use in the evaluation step. */
final class ModifiableCssStyleRule(
  private val _selector: Box[Any], /* Box[SelectorList] */
  val span: FileSpan,
  originalSel: Nullable[Any] = Nullable.empty, /* Nullable[SelectorList] */
  val fromPlainCss: Boolean = false
) extends ModifiableCssParentNode with CssStyleRule {

  /** The selector, possibly updated by the extension store. */
  def selector: Any = _selector.value

  /** The original selector before extensions. */
  val originalSelector: Any = originalSel.getOrElse(_selector.value)

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssStyleRule(this)

  def equalsIgnoringChildren(other: ModifiableCssNode): Boolean =
    other match {
      case that: ModifiableCssStyleRule => that.selector == selector
      case _ => false
    }

  def copyWithoutChildren(): ModifiableCssStyleRule =
    ModifiableCssStyleRule(_selector, span, Nullable(originalSelector), fromPlainCss)
}
