/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/expression_to_calc.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: expression_to_calc.dart -> ExpressionToCalcVisitor.scala
 *   Convention: Skeleton — returns the input expression unchanged. Full
 *               conversion logic will be added with the evaluator.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.*

/** A visitor that converts an arbitrary expression into the equivalent
  * `calc()` expression.
  *
  * Skeleton — currently a pass-through.
  */
final class ExpressionToCalcVisitor extends ExpressionVisitor[Expression] {
  def visitBinaryOperationExpression(node: BinaryOperationExpression): Expression = node
  def visitBooleanExpression(node: BooleanExpression): Expression = node
  def visitColorExpression(node: ColorExpression): Expression = node
  def visitFunctionExpression(node: FunctionExpression): Expression = node
  def visitIfExpression(node: IfExpression): Expression = node
  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Expression = node
  def visitLegacyIfExpression(node: LegacyIfExpression): Expression = node
  def visitListExpression(node: ListExpression): Expression = node
  def visitMapExpression(node: MapExpression): Expression = node
  def visitNullExpression(node: NullExpression): Expression = node
  def visitNumberExpression(node: NumberExpression): Expression = node
  def visitParenthesizedExpression(node: ParenthesizedExpression): Expression = node
  def visitSelectorExpression(node: SelectorExpression): Expression = node
  def visitStringExpression(node: StringExpression): Expression = node
  def visitSupportsExpression(node: SupportsExpression): Expression = node
  def visitUnaryOperationExpression(node: UnaryOperationExpression): Expression = node
  def visitValueExpression(node: ValueExpression): Expression = node
  def visitVariableExpression(node: VariableExpression): Expression = node
}
