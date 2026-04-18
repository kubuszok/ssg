/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/replace_expression.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: replace_expression.dart -> ReplaceExpressionVisitor.scala
 *   Convention: Dart mixin -> Scala trait with default implementations
 *   Idiom: Scala trait extends both ExpressionVisitor and IfConditionExpressionVisitor
 *          matching the Dart mixin's dual-interface implementation;
 *          visitIfExpression left as pass-through (abstract in Dart mixin)
 *   Audited: 2026-04-17
 */
package ssg
package sass
package visitor

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.ast.sass.*

/** A visitor that recursively traverses each expression in a SassScript AST and
  * replaces its contents with the values returned by nested recursion.
  *
  * In addition to the methods from [[ExpressionVisitor]], this has more general
  * protected methods that can be overridden to add behavior for a wide variety
  * of AST nodes:
  *
  *   - [[visitArgumentList]]
  *   - [[visitSupportsCondition]]
  *   - [[visitInterpolation]]
  */
trait ReplaceExpressionVisitor
    extends ExpressionVisitor[Expression]
    with IfConditionExpressionVisitor[IfConditionExpression] {

  def visitBinaryOperationExpression(node: BinaryOperationExpression): Expression =
    BinaryOperationExpression(
      node.operator,
      node.left.accept(this),
      node.right.accept(this)
    )

  def visitBooleanExpression(node: BooleanExpression): Expression = node

  def visitColorExpression(node: ColorExpression): Expression = node

  def visitFunctionExpression(node: FunctionExpression): Expression =
    FunctionExpression(
      node.originalName,
      visitArgumentList(node.arguments),
      node.span,
      namespace = node.namespace
    )

  // visitIfExpression is not implemented by the Dart mixin
  // (concrete classes must provide their own). In Scala we supply
  // a pass-through default so that the trait satisfies ExpressionVisitor.
  def visitIfExpression(node: IfExpression): Expression = node

  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Expression =
    InterpolatedFunctionExpression(
      visitInterpolation(node.name),
      visitArgumentList(node.arguments),
      node.span
    )

  def visitLegacyIfExpression(node: LegacyIfExpression): Expression =
    LegacyIfExpression(visitArgumentList(node.arguments), node.span)

  def visitListExpression(node: ListExpression): Expression =
    ListExpression(
      node.contents.map(item => item.accept(this)),
      node.separator,
      node.span,
      hasBrackets = node.hasBrackets
    )

  def visitMapExpression(node: MapExpression): Expression =
    MapExpression(
      node.pairs.map { case (key, value) =>
        (key.accept(this), value.accept(this))
      },
      node.span
    )

  def visitNullExpression(node: NullExpression): Expression = node

  def visitNumberExpression(node: NumberExpression): Expression = node

  def visitParenthesizedExpression(node: ParenthesizedExpression): Expression =
    ParenthesizedExpression(node.expression.accept(this), node.span)

  def visitSelectorExpression(node: SelectorExpression): Expression = node

  def visitStringExpression(node: StringExpression): Expression =
    StringExpression(visitInterpolation(node.text), hasQuotes = node.hasQuotes)

  def visitSupportsExpression(node: SupportsExpression): Expression =
    SupportsExpression(visitSupportsCondition(node.condition))

  def visitUnaryOperationExpression(node: UnaryOperationExpression): Expression =
    UnaryOperationExpression(
      node.operator,
      node.operand.accept(this),
      node.span
    )

  def visitValueExpression(node: ValueExpression): Expression = node

  def visitVariableExpression(node: VariableExpression): Expression = node

  // `if()` condition expressions

  def visitIfConditionParenthesized(node: IfConditionParenthesized): IfConditionExpression =
    IfConditionParenthesized(node.expression.accept(this), node.span)

  def visitIfConditionNegation(node: IfConditionNegation): IfConditionExpression =
    IfConditionNegation(node.expression.accept(this), node.span)

  def visitIfConditionOperation(node: IfConditionOperation): IfConditionExpression =
    IfConditionOperation(
      node.expressions.map(expression => expression.accept(this)),
      node.op
    )

  def visitIfConditionFunction(node: IfConditionFunction): IfConditionExpression =
    IfConditionFunction(
      visitInterpolation(node.name),
      visitInterpolation(node.arguments),
      node.span
    )

  def visitIfConditionSass(node: IfConditionSass): IfConditionExpression =
    IfConditionSass(node.expression.accept(this), node.span)

  def visitIfConditionRaw(node: IfConditionRaw): IfConditionExpression =
    IfConditionRaw(visitInterpolation(node.text))

  /** Replaces each expression in an [invocation].
    *
    * The default implementation of the visit methods calls this to replace any
    * argument invocation in an expression.
    */
  protected def visitArgumentList(invocation: ArgumentList): ArgumentList =
    new ArgumentList(
      invocation.positional.map(expression => expression.accept(this)),
      invocation.named.map { case (name, value) =>
        name -> value.accept(this)
      },
      invocation.namedSpans,
      invocation.span,
      rest = invocation.rest.map(_.accept(this)),
      keywordRest = invocation.keywordRest.map(_.accept(this))
    )

  /** Replaces each expression in [condition].
    *
    * The default implementation of the visit methods call this to visit any
    * [[SupportsCondition]] they encounter.
    */
  protected def visitSupportsCondition(condition: SupportsCondition): SupportsCondition = {
    condition match {
      case op: SupportsOperation =>
        SupportsOperation(
          visitSupportsCondition(op.left),
          visitSupportsCondition(op.right),
          op.operator,
          op.span
        )
      case neg: SupportsNegation =>
        SupportsNegation(
          visitSupportsCondition(neg.condition),
          neg.span
        )
      case interp: SupportsInterpolation =>
        SupportsInterpolation(
          interp.expression.accept(this),
          interp.span
        )
      case decl: SupportsDeclaration =>
        SupportsDeclaration(
          decl.name.accept(this),
          decl.value.accept(this),
          decl.span
        )
      case _ =>
        throw SassException(
          s"BUG: Unknown SupportsCondition $condition.",
          condition.span
        )
    }
  }

  /** Replaces each expression in an [interpolation].
    *
    * The default implementation of the visit methods call this to visit any
    * interpolation in an expression.
    */
  protected def visitInterpolation(interpolation: Interpolation): Interpolation =
    new Interpolation(
      interpolation.contents.map {
        case expr: Expression => expr.accept(this)
        case other            => other
      },
      interpolation.spans,
      interpolation.span
    )
}
