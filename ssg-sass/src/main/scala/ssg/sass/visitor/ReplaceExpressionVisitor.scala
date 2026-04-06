/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/replace_expression.dart
 * Original: Copyright (c) 2024 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: replace_expression.dart -> ReplaceExpressionVisitor.scala
 *   Convention: Skeleton — pass-through that returns the input expression.
 *               The full deep-copy logic will be added when needed.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.*

/** A visitor that recursively rewrites an expression tree.
  *
  * Skeleton — currently a pass-through.
  */
trait ReplaceExpressionVisitor extends ExpressionVisitor[Expression] {
  def visitBinaryOperationExpression(node:      BinaryOperationExpression):      Expression = node
  def visitBooleanExpression(node:              BooleanExpression):              Expression = node
  def visitColorExpression(node:                ColorExpression):                Expression = node
  def visitFunctionExpression(node:             FunctionExpression):             Expression = node
  def visitIfExpression(node:                   IfExpression):                   Expression = node
  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Expression = node
  def visitLegacyIfExpression(node:             LegacyIfExpression):             Expression = node
  def visitListExpression(node:                 ListExpression):                 Expression = node
  def visitMapExpression(node:                  MapExpression):                  Expression = node
  def visitNullExpression(node:                 NullExpression):                 Expression = node
  def visitNumberExpression(node:               NumberExpression):               Expression = node
  def visitParenthesizedExpression(node:        ParenthesizedExpression):        Expression = node
  def visitSelectorExpression(node:             SelectorExpression):             Expression = node
  def visitStringExpression(node:               StringExpression):               Expression = node
  def visitSupportsExpression(node:             SupportsExpression):             Expression = node
  def visitUnaryOperationExpression(node:       UnaryOperationExpression):       Expression = node
  def visitValueExpression(node:                ValueExpression):                Expression = node
  def visitVariableExpression(node:             VariableExpression):             Expression = node
}
