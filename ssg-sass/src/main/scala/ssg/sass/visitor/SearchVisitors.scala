/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/selector_search.dart,
 *              lib/src/visitor/statement_search.dart,
 *              lib/src/visitor/ast_search.dart
 * Original: Copyright (c) 2019, 2021, 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: three search visitors merged into SearchVisitors.scala
 *   Convention: Dart mixin with T? returns -> Scala trait with Nullable[T]
 *   Idiom: Dart T? null -> Nullable.empty; Dart null?.andThen -> Nullable flatMap;
 *          Dart list.search(fn) -> _search(list)(fn) via IterableUtil;
 *          Dart ?? -> Nullable orElse
 */
package ssg
package sass
package visitor

import ssg.sass.Nullable
import ssg.sass.ast.sass.*
import ssg.sass.ast.selector.*
import ssg.sass.util.IterableUtil

// ===========================================================================
// StatementSearchVisitor
// ===========================================================================

/** A [[StatementVisitor]] whose `visit*` methods default to returning [[Nullable.empty]], but which returns the first non-empty value returned by any method.
  *
  * This can be extended to find the first instance of particular nodes in the AST.
  *
  * This supports the same additional methods as [[RecursiveStatementVisitor]].
  */
trait StatementSearchVisitor[T] extends StatementVisitor[Nullable[T]] {

  /** Helper to call IterableUtil.search without clashing with Seq.search. */
  private def _search[E, R](iterable: Iterable[E])(callback: E => Nullable[R]): Nullable[R] =
    IterableUtil.search(iterable)(callback)

  def visitAtRootRule(node: AtRootRule): Nullable[T] =
    visitChildren(node.children.get)

  def visitAtRule(node: AtRule): Nullable[T] =
    node.children.flatMap(visitChildren)

  def visitContentBlock(node: ContentBlock): Nullable[T] =
    visitCallableDeclaration(node)

  def visitContentRule(node: ContentRule): Nullable[T] = Nullable.empty

  def visitDebugRule(node: DebugRule): Nullable[T] = Nullable.empty

  def visitDeclaration(node: Declaration): Nullable[T] =
    node.children.flatMap(visitChildren)

  def visitEachRule(node: EachRule): Nullable[T] =
    visitChildren(node.children.get)

  def visitErrorRule(node: ErrorRule): Nullable[T] = Nullable.empty

  def visitExtendRule(node: ExtendRule): Nullable[T] = Nullable.empty

  def visitForRule(node: ForRule): Nullable[T] =
    visitChildren(node.children.get)

  def visitForwardRule(node: ForwardRule): Nullable[T] = Nullable.empty

  def visitFunctionRule(node: FunctionRule): Nullable[T] =
    visitCallableDeclaration(node)

  def visitIfRule(node: IfRule): Nullable[T] = {
    val clauseResult = _search(node.clauses) { clause =>
      _search(clause.children)(child => child.accept(this))
    }
    clauseResult.orElse {
      node.lastClause.flatMap { lastClause =>
        _search(lastClause.children)(child => child.accept(this))
      }
    }
  }

  def visitImportRule(node: ImportRule): Nullable[T] = Nullable.empty

  def visitIncludeRule(node: IncludeRule): Nullable[T] =
    node.content.flatMap(visitContentBlock)

  def visitLoudComment(node: LoudComment): Nullable[T] = Nullable.empty

  def visitMediaRule(node: MediaRule): Nullable[T] =
    visitChildren(node.children.get)

  def visitMixinRule(node: MixinRule): Nullable[T] =
    visitCallableDeclaration(node)

  def visitReturnRule(node: ReturnRule): Nullable[T] = Nullable.empty

  def visitSilentComment(node: SilentComment): Nullable[T] = Nullable.empty

  def visitStyleRule(node: StyleRule): Nullable[T] =
    visitChildren(node.children.get)

  def visitStylesheet(node: Stylesheet): Nullable[T] =
    visitChildren(node.children.get)

  def visitSupportsRule(node: SupportsRule): Nullable[T] =
    visitChildren(node.children.get)

  def visitUseRule(node: UseRule): Nullable[T] = Nullable.empty

  def visitVariableDeclaration(node: VariableDeclaration): Nullable[T] = Nullable.empty

  def visitWarnRule(node: WarnRule): Nullable[T] = Nullable.empty

  def visitWhileRule(node: WhileRule): Nullable[T] =
    visitChildren(node.children.get)

  /** Visits each of [[node]]'s expressions and children.
    *
    * The default implementations of [[visitFunctionRule]] and [[visitMixinRule]] call this.
    */
  protected def visitCallableDeclaration(node: CallableDeclaration): Nullable[T] =
    visitChildren(node.childrenList)

  /** Visits each child in [[children]].
    *
    * The default implementation of the visit methods for all `ParentStatement`s call this.
    */
  protected def visitChildren(children: List[Statement]): Nullable[T] =
    _search(children)(child => child.accept(this))
}

// ===========================================================================
// SelectorSearchVisitor
// ===========================================================================

/** A [[SelectorVisitor]] whose `visit*` methods default to returning [[Nullable.empty]], but which returns the first non-empty value returned by any method.
  *
  * This can be extended to find the first instance of particular nodes in the AST.
  */
trait SelectorSearchVisitor[T] extends SelectorVisitor[Nullable[T]] {

  /** Helper to call IterableUtil.search without clashing with Seq.search. */
  private def _search[E, R](iterable: Iterable[E])(callback: E => Nullable[R]): Nullable[R] =
    IterableUtil.search(iterable)(callback)

  def visitAttributeSelector(attribute: AttributeSelector): Nullable[T] = Nullable.empty

  def visitClassSelector(klass: ClassSelector): Nullable[T] = Nullable.empty

  def visitIDSelector(id: IDSelector): Nullable[T] = Nullable.empty

  def visitParentSelector(parent: ParentSelector): Nullable[T] = Nullable.empty

  def visitPlaceholderSelector(placeholder: PlaceholderSelector): Nullable[T] = Nullable.empty

  def visitTypeSelector(tpe: TypeSelector): Nullable[T] = Nullable.empty

  def visitUniversalSelector(universal: UniversalSelector): Nullable[T] = Nullable.empty

  def visitComplexSelector(complex: ComplexSelector): Nullable[T] =
    _search(complex.components)(component => visitCompoundSelector(component.selector))

  def visitCompoundSelector(compound: CompoundSelector): Nullable[T] =
    _search(compound.components)(simple => simple.accept(this))

  def visitPseudoSelector(pseudo: PseudoSelector): Nullable[T] =
    pseudo.selector.flatMap(visitSelectorList)

  def visitSelectorList(list: SelectorList): Nullable[T] =
    _search(list.components)(visitComplexSelector)
}

// ===========================================================================
// AstSearchVisitor
// ===========================================================================

/** A visitor that recursively traverses each statement and expression in a Sass AST and returns the first non-empty result.
  *
  * This extends [[StatementSearchVisitor]] to traverse each expression in addition to each statement, as well as each selector for ASTs where `parseSelectors: true` was passed to
  * [[Stylesheet.parse]]. It supports the same additional methods as [[RecursiveAstVisitor]].
  */
trait AstSearchVisitor[T] extends StatementSearchVisitor[T] with ExpressionVisitor[Nullable[T]] with IfConditionExpressionVisitor[Nullable[T]] with InterpolatedSelectorVisitor[Nullable[T]] {

  /** Helper to call IterableUtil.search without clashing with Seq.search. */
  private def _search[E, R](iterable: Iterable[E])(callback: E => Nullable[R]): Nullable[R] =
    IterableUtil.search(iterable)(callback)

  // Rules

  override def visitAtRootRule(node: AtRootRule): Nullable[T] =
    node.query.flatMap(visitInterpolation).orElse(super.visitAtRootRule(node))

  override def visitAtRule(node: AtRule): Nullable[T] =
    visitInterpolation(node.name).orElse(node.value.flatMap(visitInterpolation)).orElse(super.visitAtRule(node))

  override def visitContentRule(node: ContentRule): Nullable[T] =
    visitArgumentList(node.arguments)

  override def visitDebugRule(node: DebugRule): Nullable[T] =
    visitExpression(node.expression)

  override def visitDeclaration(node: Declaration): Nullable[T] =
    visitInterpolation(node.name).orElse(node.value.flatMap(visitExpression)).orElse(super.visitDeclaration(node))

  override def visitEachRule(node: EachRule): Nullable[T] =
    visitExpression(node.list).orElse(super.visitEachRule(node))

  override def visitErrorRule(node: ErrorRule): Nullable[T] =
    visitExpression(node.expression)

  override def visitExtendRule(node: ExtendRule): Nullable[T] =
    visitInterpolation(node.selector)

  override def visitForRule(node: ForRule): Nullable[T] =
    visitExpression(node.from).orElse(visitExpression(node.to)).orElse(super.visitForRule(node))

  override def visitForwardRule(node: ForwardRule): Nullable[T] =
    _search(node.configuration)(variable => visitExpression(variable.expression))

  override def visitIfRule(node: IfRule): Nullable[T] = {
    val clauseResult = _search(node.clauses) { clause =>
      visitExpression(clause.expression).orElse(
        _search(clause.children)(child => child.accept(this))
      )
    }
    clauseResult.orElse {
      node.lastClause.flatMap { lastClause =>
        _search(lastClause.children)(child => child.accept(this))
      }
    }
  }

  override def visitImportRule(node: ImportRule): Nullable[T] =
    _search(node.imports) { imp =>
      imp match {
        case si: StaticImport =>
          visitInterpolation(si.url).orElse(
            si.modifiers.flatMap(visitInterpolation)
          )
        case _ => Nullable.empty
      }
    }

  override def visitIncludeRule(node: IncludeRule): Nullable[T] =
    visitArgumentList(node.arguments).orElse(super.visitIncludeRule(node))

  override def visitLoudComment(node: LoudComment): Nullable[T] =
    visitInterpolation(node.text)

  override def visitMediaRule(node: MediaRule): Nullable[T] =
    visitInterpolation(node.query).orElse(super.visitMediaRule(node))

  override def visitReturnRule(node: ReturnRule): Nullable[T] =
    visitExpression(node.expression)

  override def visitStyleRule(node: StyleRule): Nullable[T] =
    node.selector.flatMap(visitInterpolation).orElse(super.visitStyleRule(node))

  override def visitSupportsRule(node: SupportsRule): Nullable[T] =
    visitSupportsCondition(node.condition).orElse(super.visitSupportsRule(node))

  override def visitUseRule(node: UseRule): Nullable[T] =
    _search(node.configuration)(variable => visitExpression(variable.expression))

  override def visitVariableDeclaration(node: VariableDeclaration): Nullable[T] =
    visitExpression(node.expression)

  override def visitWarnRule(node: WarnRule): Nullable[T] =
    visitExpression(node.expression)

  override def visitWhileRule(node: WhileRule): Nullable[T] =
    visitExpression(node.condition).orElse(super.visitWhileRule(node))

  // Expressions

  protected def visitExpression(expression: Expression): Nullable[T] =
    expression.accept(this)

  def visitBinaryOperationExpression(node: BinaryOperationExpression): Nullable[T] =
    node.left.accept(this).orElse(node.right.accept(this))

  def visitBooleanExpression(node: BooleanExpression): Nullable[T] = Nullable.empty

  def visitColorExpression(node: ColorExpression): Nullable[T] = Nullable.empty

  def visitFunctionExpression(node: FunctionExpression): Nullable[T] =
    visitArgumentList(node.arguments)

  def visitIfExpression(node: IfExpression): Nullable[T] =
    _search(node.branches) { pair =>
      pair._1.flatMap(_.accept(this)).orElse(pair._2.accept(this))
    }

  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Nullable[T] =
    visitInterpolation(node.name).orElse(visitArgumentList(node.arguments))

  def visitLegacyIfExpression(node: LegacyIfExpression): Nullable[T] =
    visitArgumentList(node.arguments)

  def visitListExpression(node: ListExpression): Nullable[T] =
    _search(node.contents)(item => item.accept(this))

  def visitMapExpression(node: MapExpression): Nullable[T] =
    _search(node.pairs) { pair =>
      pair._1.accept(this).orElse(pair._2.accept(this))
    }

  def visitNullExpression(node: NullExpression): Nullable[T] = Nullable.empty

  def visitNumberExpression(node: NumberExpression): Nullable[T] = Nullable.empty

  def visitParenthesizedExpression(node: ParenthesizedExpression): Nullable[T] =
    node.expression.accept(this)

  def visitSelectorExpression(node: SelectorExpression): Nullable[T] = Nullable.empty

  def visitStringExpression(node: StringExpression): Nullable[T] =
    visitInterpolation(node.text)

  def visitSupportsExpression(node: SupportsExpression): Nullable[T] =
    visitSupportsCondition(node.condition)

  def visitUnaryOperationExpression(node: UnaryOperationExpression): Nullable[T] =
    node.operand.accept(this)

  def visitValueExpression(node: ValueExpression): Nullable[T] = Nullable.empty

  def visitVariableExpression(node: VariableExpression): Nullable[T] = Nullable.empty

  // `if()` condition expressions

  def visitIfConditionParenthesized(node: IfConditionParenthesized): Nullable[T] =
    node.expression.accept(this)

  def visitIfConditionNegation(node: IfConditionNegation): Nullable[T] =
    node.expression.accept(this)

  def visitIfConditionOperation(node: IfConditionOperation): Nullable[T] =
    _search(node.expressions)(expression => expression.accept(this))

  def visitIfConditionFunction(node: IfConditionFunction): Nullable[T] =
    visitInterpolation(node.name).orElse(visitInterpolation(node.arguments))

  def visitIfConditionSass(node: IfConditionSass): Nullable[T] =
    node.expression.accept(this)

  def visitIfConditionRaw(node: IfConditionRaw): Nullable[T] =
    visitInterpolation(node.text)

  // Interpolated selectors

  def visitAttributeSelector(node: InterpolatedAttributeSelector): Nullable[T] =
    visitQualifiedName(node.name).orElse(node.value.flatMap(visitInterpolation)).orElse(node.modifier.flatMap(visitInterpolation))

  def visitClassSelector(node: InterpolatedClassSelector): Nullable[T] =
    visitInterpolation(node.name)

  def visitComplexSelector(node: InterpolatedComplexSelector): Nullable[T] =
    _search(node.components)(component => visitCompoundSelector(component.selector))

  def visitCompoundSelector(node: InterpolatedCompoundSelector): Nullable[T] =
    _search(node.components)(simple => simple.accept(this))

  def visitIDSelector(node: InterpolatedIDSelector): Nullable[T] =
    visitInterpolation(node.name)

  def visitParentSelector(node: InterpolatedParentSelector): Nullable[T] =
    node.suffix.flatMap(visitInterpolation)

  def visitPlaceholderSelector(node: InterpolatedPlaceholderSelector): Nullable[T] =
    visitInterpolation(node.name)

  def visitPseudoSelector(node: InterpolatedPseudoSelector): Nullable[T] =
    visitInterpolation(node.name).orElse(node.argument.flatMap(visitInterpolation)).orElse(node.selector.flatMap(visitSelectorList))

  def visitSelectorList(node: InterpolatedSelectorList): Nullable[T] =
    _search(node.components)(component => visitComplexSelector(component))

  def visitTypeSelector(node: InterpolatedTypeSelector): Nullable[T] =
    visitQualifiedName(node.name)

  def visitUniversalSelector(node: InterpolatedUniversalSelector): Nullable[T] =
    node.namespace.flatMap(visitInterpolation)

  override protected def visitCallableDeclaration(node: CallableDeclaration): Nullable[T] =
    _search(node.parameters.parameters) { parameter =>
      parameter.defaultValue.flatMap(visitExpression)
    }.orElse(super.visitCallableDeclaration(node))

  /** Visits each expression in an [invocation].
    *
    * The default implementation of the visit methods calls this to visit any argument invocation in a statement.
    */
  protected def visitArgumentList(invocation: ArgumentList): Nullable[T] =
    _search(invocation.positional)(expression => visitExpression(expression))
      .orElse(_search(invocation.named.values)(expression => visitExpression(expression)))
      .orElse(invocation.rest.flatMap(visitExpression))
      .orElse(invocation.keywordRest.flatMap(visitExpression))

  /** Visits each expression in [condition].
    *
    * The default implementation of the visit methods call this to visit any [[SupportsCondition]] they encounter.
    */
  protected def visitSupportsCondition(condition: SupportsCondition): Nullable[T] =
    condition match {
      case op: SupportsOperation =>
        visitSupportsCondition(op.left).orElse(visitSupportsCondition(op.right))
      case neg: SupportsNegation =>
        visitSupportsCondition(neg.condition)
      case interp: SupportsInterpolation =>
        visitExpression(interp.expression)
      case decl: SupportsDeclaration =>
        visitExpression(decl.name).orElse(visitExpression(decl.value))
      case _ => Nullable.empty
    }

  /** Visits each expression in an [interpolation].
    *
    * The default implementation of the visit methods call this to visit any interpolation in a statement.
    */
  protected def visitInterpolation(interpolation: Interpolation): Nullable[T] =
    _search(interpolation.contents) { node =>
      node match {
        case expr: Expression => visitExpression(expr)
        case _ => Nullable.empty
      }
    }

  /** Visits each expression in [node].
    *
    * The default implementation of the visit methods call this to visit any qualified names in a selector.
    */
  protected def visitQualifiedName(node: InterpolatedQualifiedName): Nullable[T] =
    node.namespace.flatMap(visitInterpolation).orElse(visitInterpolation(node.name))
}
