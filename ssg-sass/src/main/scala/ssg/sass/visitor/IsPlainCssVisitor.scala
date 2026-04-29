/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/is_plain_css.dart
 * Original: Copyright (c) 2026 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: is_plain_css.dart -> IsPlainCssVisitor.scala
 *   Convention: Dart class -> Scala final class
 *   Idiom: ExpressionVisitor + IfConditionExpressionVisitor
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/visitor/is_plain_css.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package visitor

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.ast.sass.*

// We could use [AstSearchVisitor] to implement this more tersely, but that
// would default to returning `true` if we added a new expression type and
// forgot to update this class.

/** A visitor that determines whether an expression is valid plain CSS that will produce the same result as it would in Sass.
  *
  * This should be used through [Expression.isPlainCss].
  *
  * @param allowInterpolation
  *   if true, interpolated expressions are allowed as an exception, even if they contain SassScript.
  */
final class IsPlainCssVisitor(allowInterpolation: Boolean = false) extends ExpressionVisitor[Boolean] with IfConditionExpressionVisitor[Boolean] {

  def visitBinaryOperationExpression(node: BinaryOperationExpression): Boolean = false

  def visitBooleanExpression(node: BooleanExpression): Boolean = false

  def visitColorExpression(node: ColorExpression): Boolean = true

  def visitFunctionExpression(node: FunctionExpression): Boolean =
    node.namespace.isEmpty && _visitArgumentList(node.arguments)

  def visitIfExpression(node: IfExpression): Boolean =
    node.branches.forall { case (condition, branch) =>
      if (condition.isDefined) condition.get.accept(this) && branch.accept(this)
      else branch.accept(this)
    }

  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Boolean =
    allowInterpolation && _visitArgumentList(node.arguments)

  def visitLegacyIfExpression(node: LegacyIfExpression): Boolean = false

  def visitListExpression(node: ListExpression): Boolean =
    (node.contents.nonEmpty || node.hasBrackets) &&
      node.contents.forall(_.accept(this))

  def visitMapExpression(node: MapExpression): Boolean = false

  def visitNullExpression(node: NullExpression): Boolean = false

  def visitNumberExpression(node: NumberExpression): Boolean = true

  def visitParenthesizedExpression(node: ParenthesizedExpression): Boolean =
    node.expression.accept(this)

  def visitSelectorExpression(node: SelectorExpression): Boolean = false

  def visitStringExpression(node: StringExpression): Boolean =
    allowInterpolation || node.text.isPlain

  def visitSupportsExpression(node: SupportsExpression): Boolean = false

  def visitUnaryOperationExpression(node: UnaryOperationExpression): Boolean = false

  def visitValueExpression(node: ValueExpression): Boolean = false

  def visitVariableExpression(node: VariableExpression): Boolean = false

  def visitIfConditionParenthesized(node: IfConditionParenthesized): Boolean =
    node.expression.accept(this)

  def visitIfConditionNegation(node: IfConditionNegation): Boolean =
    node.expression.accept(this)

  def visitIfConditionOperation(node: IfConditionOperation): Boolean =
    node.expressions.forall(_.accept(this))

  def visitIfConditionFunction(node: IfConditionFunction): Boolean =
    allowInterpolation || (node.name.isPlain && node.arguments.isPlain)

  def visitIfConditionSass(node: IfConditionSass): Boolean = false

  def visitIfConditionRaw(node: IfConditionRaw): Boolean =
    allowInterpolation || node.text.isPlain

  /** Returns whether [arguments] contains only plain CSS. */
  private def _visitArgumentList(node: ArgumentList): Boolean =
    node.named.isEmpty &&
      node.rest.isEmpty &&
      node.positional.forall(_.accept(this))
}
