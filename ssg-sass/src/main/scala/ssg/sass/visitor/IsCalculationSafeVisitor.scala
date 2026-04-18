/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/is_calculation_safe.dart
 * Original: Copyright (c) 2024 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: is_calculation_safe.dart -> IsCalculationSafeVisitor.scala
 *   Convention: Dart class -> Scala final class
 *   Idiom: codeUnitAtOrNull -> safe charAt with length guard
 *   Audited: 2026-04-17
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.*
import ssg.sass.value.ListSeparator

// We could use [AstSearchVisitor] to implement this more tersely, but that
// would default to returning `true` if we added a new expression type and
// forgot to update this class.

/** A visitor that determines whether an expression is valid in a calculation
  * context.
  *
  * This should be used through [[Expression.isCalculationSafe]].
  */
final class IsCalculationSafeVisitor extends ExpressionVisitor[Boolean] {

  def visitBinaryOperationExpression(node: BinaryOperationExpression): Boolean =
    (node.operator == BinaryOperator.Times ||
      node.operator == BinaryOperator.DividedBy ||
      node.operator == BinaryOperator.Plus ||
      node.operator == BinaryOperator.Minus) &&
      node.left.accept(this) &&
      node.right.accept(this)

  def visitBooleanExpression(node: BooleanExpression): Boolean = false

  def visitColorExpression(node: ColorExpression): Boolean = false

  def visitFunctionExpression(node: FunctionExpression): Boolean = true

  def visitIfExpression(node: IfExpression): Boolean = true

  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Boolean = true

  def visitLegacyIfExpression(node: LegacyIfExpression): Boolean = true

  def visitListExpression(node: ListExpression): Boolean =
    node.separator == ListSeparator.Space &&
      !node.hasBrackets &&
      node.contents.length > 1 &&
      node.contents.forall(expression => expression.accept(this))

  def visitMapExpression(node: MapExpression): Boolean = false

  def visitNullExpression(node: NullExpression): Boolean = false

  def visitNumberExpression(node: NumberExpression): Boolean = true

  def visitParenthesizedExpression(node: ParenthesizedExpression): Boolean =
    node.expression.accept(this)

  def visitSelectorExpression(node: SelectorExpression): Boolean = false

  def visitStringExpression(node: StringExpression): Boolean = {
    if (node.hasQuotes) false
    else {
      // Exclude non-identifier constructs that are parsed as [StringExpression]s.
      // We could just check if they parse as valid identifiers, but this is
      // cheaper.
      val text = node.text.initialPlain
      // !important
      !text.startsWith("!") &&
      // ID-style identifiers
      !text.startsWith("#") &&
      // Unicode ranges
      !(text.length > 1 && text.charAt(1) == '+') &&
      // url()
      !(text.length > 3 && text.charAt(3) == '(')
    }
  }

  def visitSupportsExpression(node: SupportsExpression): Boolean = false

  def visitUnaryOperationExpression(node: UnaryOperationExpression): Boolean = false

  def visitValueExpression(node: ValueExpression): Boolean = false

  def visitVariableExpression(node: VariableExpression): Boolean = true
}
