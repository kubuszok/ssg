/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/is_calculation_safe.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: is_calculation_safe.dart -> IsCalculationSafeVisitor.scala
 *   Convention: Skeleton — defaults to false. The actual rules will be
 *               implemented alongside the evaluator.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.*

/** A visitor that determines whether an expression is "calculation-safe" —
  * that is, whether it can appear inside a `calc()` expression.
  *
  * Skeleton — defaults to false.
  */
final class IsCalculationSafeVisitor extends ExpressionVisitor[Boolean] {
  def visitBinaryOperationExpression(node: BinaryOperationExpression): Boolean = false
  def visitBooleanExpression(node: BooleanExpression): Boolean = false
  def visitColorExpression(node: ColorExpression): Boolean = false
  def visitFunctionExpression(node: FunctionExpression): Boolean = false
  def visitIfExpression(node: IfExpression): Boolean = false
  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Boolean = false
  def visitLegacyIfExpression(node: LegacyIfExpression): Boolean = false
  def visitListExpression(node: ListExpression): Boolean = false
  def visitMapExpression(node: MapExpression): Boolean = false
  def visitNullExpression(node: NullExpression): Boolean = false
  def visitNumberExpression(node: NumberExpression): Boolean = false
  def visitParenthesizedExpression(node: ParenthesizedExpression): Boolean = false
  def visitSelectorExpression(node: SelectorExpression): Boolean = false
  def visitStringExpression(node: StringExpression): Boolean = false
  def visitSupportsExpression(node: SupportsExpression): Boolean = false
  def visitUnaryOperationExpression(node: UnaryOperationExpression): Boolean = false
  def visitValueExpression(node: ValueExpression): Boolean = false
  def visitVariableExpression(node: VariableExpression): Boolean = false
}
