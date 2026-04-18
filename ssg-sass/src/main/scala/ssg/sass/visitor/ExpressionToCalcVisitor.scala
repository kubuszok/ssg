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
 *   Convention: Dart top-level function `expressionToCalc` -> companion object method;
 *               Dart private `_MakeExpressionCalculationSafe` class -> Scala final class
 *   Idiom: `with ReplaceExpressionVisitor` -> `extends ReplaceExpressionVisitor`;
 *          only overrides that differ from the base trait are provided
 *   Audited: 2026-04-17
 */
package ssg
package sass
package visitor

import ssg.sass.Nullable
import ssg.sass.ast.sass.*

/** A visitor that replaces constructs that can't be used in a calculation with
  * those that can.
  */
final class ExpressionToCalcVisitor extends ReplaceExpressionVisitor {

  override def visitBinaryOperationExpression(node: BinaryOperationExpression): Expression =
    if (node.operator == BinaryOperator.Modulo) {
      // `calc()` doesn't support `%` for modulo but Sass doesn't yet support the
      // `mod()` calculation function because there's no browser support, so we have
      // to work around it by wrapping the call in a Sass function.
      FunctionExpression(
        "max",
        new ArgumentList(List(node), Map.empty, Map.empty, node.span),
        node.span,
        namespace = Nullable("math")
      )
    } else {
      super.visitBinaryOperationExpression(node)
    }

  override def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Expression =
    node

  override def visitIfExpression(node: IfExpression): Expression = node

  override def visitUnaryOperationExpression(node: UnaryOperationExpression): Expression =
    node.operator match {
      // `calc()` doesn't support unary operations.
      case UnaryOperator.Plus => node.operand
      case UnaryOperator.Minus =>
        BinaryOperationExpression(
          BinaryOperator.Times,
          NumberExpression(-1, node.span),
          node.operand
        )
      case _ =>
        // Other unary operations don't produce numbers, so keep them as-is to
        // give the user a more useful syntax error after serialization.
        super.visitUnaryOperationExpression(node)
    }
}

object ExpressionToCalcVisitor {

  /** Converts [expression] to an equivalent `calc()`.
    *
    * This assumes that [expression] already returns a number. It's intended for
    * use in end-user messaging, and may not produce directly evaluable
    * expressions.
    */
  def expressionToCalc(expression: Expression): FunctionExpression =
    FunctionExpression(
      "calc",
      new ArgumentList(
        List(expression.accept(new ExpressionToCalcVisitor())),
        Map.empty,
        Map.empty,
        expression.span
      ),
      expression.span
    )
}
