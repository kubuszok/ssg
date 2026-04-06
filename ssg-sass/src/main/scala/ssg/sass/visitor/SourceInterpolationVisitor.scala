/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/source_interpolation.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: source_interpolation.dart -> SourceInterpolationVisitor.scala
 *   Convention: Skeleton — defaults all visit methods to Nullable.empty.
 *               The actual interpolation reconstruction will be implemented
 *               alongside the evaluator.
 */
package ssg
package sass
package visitor

import ssg.sass.Nullable
import ssg.sass.ast.sass.*

/** A visitor that returns the source-text interpolation that produced a given
  * expression, when one is available.
  *
  * Skeleton — defaults to [[Nullable.empty]].
  */
final class SourceInterpolationVisitor extends ExpressionVisitor[Nullable[Interpolation]] {
  def visitBinaryOperationExpression(node: BinaryOperationExpression): Nullable[Interpolation] = Nullable.empty
  def visitBooleanExpression(node: BooleanExpression): Nullable[Interpolation] = Nullable.empty
  def visitColorExpression(node: ColorExpression): Nullable[Interpolation] = Nullable.empty
  def visitFunctionExpression(node: FunctionExpression): Nullable[Interpolation] = Nullable.empty
  def visitIfExpression(node: IfExpression): Nullable[Interpolation] = Nullable.empty
  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Nullable[Interpolation] = Nullable.empty
  def visitLegacyIfExpression(node: LegacyIfExpression): Nullable[Interpolation] = Nullable.empty
  def visitListExpression(node: ListExpression): Nullable[Interpolation] = Nullable.empty
  def visitMapExpression(node: MapExpression): Nullable[Interpolation] = Nullable.empty
  def visitNullExpression(node: NullExpression): Nullable[Interpolation] = Nullable.empty
  def visitNumberExpression(node: NumberExpression): Nullable[Interpolation] = Nullable.empty
  def visitParenthesizedExpression(node: ParenthesizedExpression): Nullable[Interpolation] = Nullable.empty
  def visitSelectorExpression(node: SelectorExpression): Nullable[Interpolation] = Nullable.empty
  def visitStringExpression(node: StringExpression): Nullable[Interpolation] = Nullable.empty
  def visitSupportsExpression(node: SupportsExpression): Nullable[Interpolation] = Nullable.empty
  def visitUnaryOperationExpression(node: UnaryOperationExpression): Nullable[Interpolation] = Nullable.empty
  def visitValueExpression(node: ValueExpression): Nullable[Interpolation] = Nullable.empty
  def visitVariableExpression(node: VariableExpression): Nullable[Interpolation] = Nullable.empty
}
