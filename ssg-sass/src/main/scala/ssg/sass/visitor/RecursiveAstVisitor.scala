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
 *   Idiom: Dart null?.andThen -> Nullable foreach/map;
 *          Dart `for (var x in collection)` -> `for (x <- collection)`;
 *          Dart `is StaticImport` -> `case si: StaticImport`
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.*

/** A visitor that recursively traverses each statement and expression in a Sass AST.
  *
  * This extends [[RecursiveStatementVisitor]] to traverse each expression in addition to each statement. It adds even more protected methods:
  *
  *   - [[visitArgumentList]]
  *   - [[visitSupportsCondition]]
  *   - [[visitInterpolation]]
  *   - [[visitQualifiedName]]
  */
trait RecursiveAstVisitor extends RecursiveStatementVisitor with ExpressionVisitor[Unit] with IfConditionExpressionVisitor[Unit] with InterpolatedSelectorVisitor[Unit] {

  override def visitAtRootRule(node: AtRootRule): Unit = {
    node.query.foreach(visitInterpolation)
    super.visitAtRootRule(node)
  }

  override def visitAtRule(node: AtRule): Unit = {
    visitInterpolation(node.name)
    node.value.foreach(visitInterpolation)
    super.visitAtRule(node)
  }

  override def visitContentRule(node: ContentRule): Unit =
    visitArgumentList(node.arguments)

  override def visitDebugRule(node: DebugRule): Unit =
    visitExpression(node.expression)

  override def visitDeclaration(node: Declaration): Unit = {
    visitInterpolation(node.name)
    node.value.foreach(visitExpression)
    super.visitDeclaration(node)
  }

  override def visitEachRule(node: EachRule): Unit = {
    visitExpression(node.list)
    super.visitEachRule(node)
  }

  override def visitErrorRule(node: ErrorRule): Unit =
    visitExpression(node.expression)

  override def visitExtendRule(node: ExtendRule): Unit =
    visitInterpolation(node.selector)

  override def visitForRule(node: ForRule): Unit = {
    visitExpression(node.from)
    visitExpression(node.to)
    super.visitForRule(node)
  }

  override def visitIfRule(node: IfRule): Unit = {
    for (clause <- node.clauses) {
      visitExpression(clause.expression)
      for (child <- clause.children)
        child.accept(this)
    }

    node.lastClause.foreach { lastClause =>
      for (child <- lastClause.children)
        child.accept(this)
    }
  }

  override def visitImportRule(node: ImportRule): Unit = {
    for (imp <- node.imports) {
      imp match {
        case si: StaticImport =>
          visitInterpolation(si.url)
          si.modifiers.foreach(visitInterpolation)
        case _ => ()
      }
    }
  }

  override def visitIncludeRule(node: IncludeRule): Unit = {
    visitArgumentList(node.arguments)
    super.visitIncludeRule(node)
  }

  override def visitLoudComment(node: LoudComment): Unit =
    visitInterpolation(node.text)

  override def visitMediaRule(node: MediaRule): Unit = {
    visitInterpolation(node.query)
    super.visitMediaRule(node)
  }

  override def visitReturnRule(node: ReturnRule): Unit =
    visitExpression(node.expression)

  override def visitStyleRule(node: StyleRule): Unit = {
    node.selector.foreach(visitInterpolation)
    super.visitStyleRule(node)
  }

  override def visitSupportsRule(node: SupportsRule): Unit = {
    visitSupportsCondition(node.condition)
    super.visitSupportsRule(node)
  }

  override def visitUseRule(node: UseRule): Unit =
    for (variable <- node.configuration)
      visitExpression(variable.expression)

  override def visitVariableDeclaration(node: VariableDeclaration): Unit =
    visitExpression(node.expression)

  override def visitWarnRule(node: WarnRule): Unit =
    visitExpression(node.expression)

  override def visitWhileRule(node: WhileRule): Unit = {
    visitExpression(node.condition)
    super.visitWhileRule(node)
  }

  // Expressions

  /** Visits a generic expression — dispatches via [[Expression#accept]]. */
  protected def visitExpression(expression: Expression): Unit =
    expression.accept(this)

  def visitBinaryOperationExpression(node: BinaryOperationExpression): Unit = {
    node.left.accept(this)
    node.right.accept(this)
  }

  def visitBooleanExpression(node: BooleanExpression): Unit = ()

  def visitColorExpression(node: ColorExpression): Unit = ()

  override def visitForwardRule(node: ForwardRule): Unit =
    for (variable <- node.configuration)
      visitExpression(variable.expression)

  def visitFunctionExpression(node: FunctionExpression): Unit =
    visitArgumentList(node.arguments)

  def visitIfExpression(node: IfExpression): Unit =
    for ((condition, value) <- node.branches) {
      condition.foreach(_.accept(this))
      value.accept(this)
    }

  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Unit = {
    visitInterpolation(node.name)
    visitArgumentList(node.arguments)
  }

  def visitLegacyIfExpression(node: LegacyIfExpression): Unit =
    visitArgumentList(node.arguments)

  def visitListExpression(node: ListExpression): Unit =
    for (item <- node.contents)
      item.accept(this)

  def visitMapExpression(node: MapExpression): Unit =
    for ((key, value) <- node.pairs) {
      key.accept(this)
      value.accept(this)
    }

  def visitNullExpression(node: NullExpression): Unit = ()

  def visitNumberExpression(node: NumberExpression): Unit = ()

  def visitParenthesizedExpression(node: ParenthesizedExpression): Unit =
    node.expression.accept(this)

  def visitSelectorExpression(node: SelectorExpression): Unit = ()

  def visitStringExpression(node: StringExpression): Unit =
    visitInterpolation(node.text)

  def visitSupportsExpression(node: SupportsExpression): Unit =
    visitSupportsCondition(node.condition)

  def visitUnaryOperationExpression(node: UnaryOperationExpression): Unit =
    node.operand.accept(this)

  def visitValueExpression(node: ValueExpression): Unit = ()

  def visitVariableExpression(node: VariableExpression): Unit = ()

  // `if()` condition expressions

  def visitIfConditionParenthesized(node: IfConditionParenthesized): Unit =
    node.expression.accept(this)

  def visitIfConditionNegation(node: IfConditionNegation): Unit =
    node.expression.accept(this)

  def visitIfConditionOperation(node: IfConditionOperation): Unit =
    for (expr <- node.expressions)
      expr.accept(this)

  def visitIfConditionFunction(node: IfConditionFunction): Unit = {
    visitInterpolation(node.name)
    visitInterpolation(node.arguments)
  }

  def visitIfConditionSass(node: IfConditionSass): Unit =
    node.expression.accept(this)

  def visitIfConditionRaw(node: IfConditionRaw): Unit =
    visitInterpolation(node.text)

  // Interpolated selectors

  def visitAttributeSelector(node: InterpolatedAttributeSelector): Unit = {
    visitQualifiedName(node.name)
    node.value.foreach(visitInterpolation)
    node.modifier.foreach(visitInterpolation)
  }

  def visitClassSelector(node: InterpolatedClassSelector): Unit =
    visitInterpolation(node.name)

  def visitComplexSelector(node: InterpolatedComplexSelector): Unit =
    for (component <- node.components)
      visitCompoundSelector(component.selector)

  def visitCompoundSelector(node: InterpolatedCompoundSelector): Unit =
    for (simple <- node.components)
      simple.accept(this)

  def visitIDSelector(node: InterpolatedIDSelector): Unit =
    visitInterpolation(node.name)

  def visitParentSelector(node: InterpolatedParentSelector): Unit =
    node.suffix.foreach(visitInterpolation)

  def visitPlaceholderSelector(node: InterpolatedPlaceholderSelector): Unit =
    visitInterpolation(node.name)

  def visitPseudoSelector(node: InterpolatedPseudoSelector): Unit = {
    visitInterpolation(node.name)
    node.argument.foreach(visitInterpolation)
    node.selector.foreach(visitSelectorList)
  }

  def visitSelectorList(node: InterpolatedSelectorList): Unit =
    for (component <- node.components)
      visitComplexSelector(component)

  def visitTypeSelector(node: InterpolatedTypeSelector): Unit =
    visitQualifiedName(node.name)

  def visitUniversalSelector(node: InterpolatedUniversalSelector): Unit =
    node.namespace.foreach(visitInterpolation)

  override protected def visitCallableDeclaration(node: CallableDeclaration): Unit = {
    for (parameter <- node.parameters.parameters)
      parameter.defaultValue.foreach(visitExpression)
    super.visitCallableDeclaration(node)
  }

  /** Visits each expression in an [invocation].
    *
    * The default implementation of the visit methods calls this to visit any argument invocation in a statement.
    */
  protected def visitArgumentList(invocation: ArgumentList): Unit = {
    for (expression <- invocation.positional)
      visitExpression(expression)
    for (expression <- invocation.named.values)
      visitExpression(expression)
    invocation.rest.foreach(visitExpression)
    invocation.keywordRest.foreach(visitExpression)
  }

  /** Visits each expression in [condition].
    *
    * The default implementation of the visit methods call this to visit any [[SupportsCondition]] they encounter.
    */
  protected def visitSupportsCondition(condition: SupportsCondition): Unit =
    condition match {
      case op: SupportsOperation =>
        visitSupportsCondition(op.left)
        visitSupportsCondition(op.right)
      case neg: SupportsNegation =>
        visitSupportsCondition(neg.condition)
      case interp: SupportsInterpolation =>
        visitExpression(interp.expression)
      case decl: SupportsDeclaration =>
        visitExpression(decl.name)
        visitExpression(decl.value)
    }

  /** Visits each expression in an [interpolation].
    *
    * The default implementation of the visit methods call this to visit any interpolation in a statement.
    */
  protected def visitInterpolation(interpolation: Interpolation): Unit =
    for (node <- interpolation.contents)
      node match {
        case expr: Expression => visitExpression(expr)
        case _ => ()
      }

  /** Visits each interpolation in [node].
    *
    * The default implementation of the visit methods calls this to visit any qualified names in a selector.
    */
  protected def visitQualifiedName(node: InterpolatedQualifiedName): Unit = {
    node.namespace.foreach(visitInterpolation)
    visitInterpolation(node.name)
  }
}
