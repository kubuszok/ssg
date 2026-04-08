/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/is_plain_css.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: is_plain_css.dart -> IsPlainCssVisitor.scala
 *   Convention: Skeleton — defaults all visit methods to false. The full
 *               predicate logic will be ported alongside the evaluator.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.*

/** A visitor that determines whether an expression is plain CSS — that is, whether it can appear in plain-CSS output without any Sass-specific transformations.
  *
  * Skeleton — defaults to false.
  */
final class IsPlainCssVisitor extends ExpressionVisitor[Boolean] {
  def visitBinaryOperationExpression(node:      BinaryOperationExpression):      Boolean = false
  def visitBooleanExpression(node:              BooleanExpression):              Boolean = false
  def visitColorExpression(node:                ColorExpression):                Boolean = false
  def visitFunctionExpression(node:             FunctionExpression):             Boolean = false
  def visitIfExpression(node:                   IfExpression):                   Boolean = false
  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Boolean = false
  def visitLegacyIfExpression(node:             LegacyIfExpression):             Boolean = false
  def visitListExpression(node:                 ListExpression):                 Boolean = false
  def visitMapExpression(node:                  MapExpression):                  Boolean = false
  def visitNullExpression(node:                 NullExpression):                 Boolean = false
  def visitNumberExpression(node:               NumberExpression):               Boolean = false
  def visitParenthesizedExpression(node:        ParenthesizedExpression):        Boolean = false
  def visitSelectorExpression(node:             SelectorExpression):             Boolean = false
  def visitStringExpression(node:               StringExpression):               Boolean = false
  def visitSupportsExpression(node:             SupportsExpression):             Boolean = false
  def visitUnaryOperationExpression(node:       UnaryOperationExpression):       Boolean = false
  def visitValueExpression(node:                ValueExpression):                Boolean = false
  def visitVariableExpression(node:             VariableExpression):             Boolean = false
}
