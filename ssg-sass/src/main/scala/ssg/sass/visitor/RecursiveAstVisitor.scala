/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/recursive_ast.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: recursive_ast.dart -> RecursiveAstVisitor.scala
 *   Convention: Dart mixin -> Scala trait extending RecursiveStatementVisitor
 *   Idiom: Skeleton — most expression/interpolated-selector visit methods
 *          default to no-op. Concrete subclasses override the ones they need.
 *          Full traversal will be implemented when the evaluator is ported.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.*

/** A visitor that recursively traverses each statement and expression in a
  * Sass AST.
  *
  * This extends [[RecursiveStatementVisitor]] to traverse each expression in
  * addition to each statement.
  *
  * NOTE: Skeleton implementation. The default expression and
  * interpolated-selector visit methods are no-ops; concrete subclasses must
  * override traversal as needed. Full recursive expression traversal will be
  * filled in alongside the evaluator port.
  */
trait RecursiveAstVisitor
    extends RecursiveStatementVisitor
    with ExpressionVisitor[Unit]
    with IfConditionExpressionVisitor[Unit]
    with InterpolatedSelectorVisitor[Unit] {

  // ---- ExpressionVisitor ---------------------------------------------------

  def visitBinaryOperationExpression(node: BinaryOperationExpression): Unit = ()
  def visitBooleanExpression(node: BooleanExpression): Unit = ()
  def visitColorExpression(node: ColorExpression): Unit = ()
  def visitFunctionExpression(node: FunctionExpression): Unit = ()
  def visitIfExpression(node: IfExpression): Unit = ()
  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Unit = ()
  def visitLegacyIfExpression(node: LegacyIfExpression): Unit = ()
  def visitListExpression(node: ListExpression): Unit = ()
  def visitMapExpression(node: MapExpression): Unit = ()
  def visitNullExpression(node: NullExpression): Unit = ()
  def visitNumberExpression(node: NumberExpression): Unit = ()
  def visitParenthesizedExpression(node: ParenthesizedExpression): Unit = ()
  def visitSelectorExpression(node: SelectorExpression): Unit = ()
  def visitStringExpression(node: StringExpression): Unit = ()
  def visitSupportsExpression(node: SupportsExpression): Unit = ()
  def visitUnaryOperationExpression(node: UnaryOperationExpression): Unit = ()
  def visitValueExpression(node: ValueExpression): Unit = ()
  def visitVariableExpression(node: VariableExpression): Unit = ()

  // ---- IfConditionExpressionVisitor ---------------------------------------

  def visitIfConditionParenthesized(node: IfConditionParenthesized): Unit = ()
  def visitIfConditionNegation(node: IfConditionNegation): Unit = ()
  def visitIfConditionOperation(node: IfConditionOperation): Unit = ()
  def visitIfConditionFunction(node: IfConditionFunction): Unit = ()
  def visitIfConditionSass(node: IfConditionSass): Unit = ()
  def visitIfConditionRaw(node: IfConditionRaw): Unit = ()

  // ---- InterpolatedSelectorVisitor ----------------------------------------

  def visitSelectorList(node: InterpolatedSelectorList): Unit = ()
  def visitComplexSelector(node: InterpolatedComplexSelector): Unit = ()
  def visitCompoundSelector(node: InterpolatedCompoundSelector): Unit = ()
  def visitAttributeSelector(node: InterpolatedAttributeSelector): Unit = ()
  def visitClassSelector(node: InterpolatedClassSelector): Unit = ()
  def visitIDSelector(node: InterpolatedIDSelector): Unit = ()
  def visitParentSelector(node: InterpolatedParentSelector): Unit = ()
  def visitPlaceholderSelector(node: InterpolatedPlaceholderSelector): Unit = ()
  def visitPseudoSelector(node: InterpolatedPseudoSelector): Unit = ()
  def visitTypeSelector(node: InterpolatedTypeSelector): Unit = ()
  def visitUniversalSelector(node: InterpolatedUniversalSelector): Unit = ()

  /** Visits a generic expression — dispatches via [[Expression#accept]]. */
  protected def visitExpression(expression: Expression): Unit =
    expression.accept(this)
}
