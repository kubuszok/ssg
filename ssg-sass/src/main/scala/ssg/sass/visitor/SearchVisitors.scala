/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/selector_search.dart,
 *              lib/src/visitor/statement_search.dart,
 *              lib/src/visitor/ast_search.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: three search visitors merged into SearchVisitors.scala
 *   Convention: Dart mixin with T? returns -> Scala trait with Nullable[T]
 *   Idiom: Skeleton — concrete subclasses override visit methods to return
 *          a value when they find a match. Default traversal is left for
 *          implementation alongside the evaluator port.
 */
package ssg
package sass
package visitor

import ssg.sass.Nullable
import ssg.sass.ast.sass.*
import ssg.sass.ast.selector.*

/** A visitor that recursively traverses each statement in a Sass AST and
  * returns the first non-empty result.
  *
  * Skeleton — defaults all returns to [[Nullable.empty]]; concrete subclasses
  * override the visit methods they care about.
  */
trait StatementSearchVisitor[T] extends StatementVisitor[Nullable[T]] {

  def visitAtRootRule(node: AtRootRule): Nullable[T] = Nullable.empty
  def visitAtRule(node: AtRule): Nullable[T] = Nullable.empty
  def visitContentBlock(node: ContentBlock): Nullable[T] = Nullable.empty
  def visitContentRule(node: ContentRule): Nullable[T] = Nullable.empty
  def visitDebugRule(node: DebugRule): Nullable[T] = Nullable.empty
  def visitDeclaration(node: Declaration): Nullable[T] = Nullable.empty
  def visitEachRule(node: EachRule): Nullable[T] = Nullable.empty
  def visitErrorRule(node: ErrorRule): Nullable[T] = Nullable.empty
  def visitExtendRule(node: ExtendRule): Nullable[T] = Nullable.empty
  def visitForRule(node: ForRule): Nullable[T] = Nullable.empty
  def visitForwardRule(node: ForwardRule): Nullable[T] = Nullable.empty
  def visitFunctionRule(node: FunctionRule): Nullable[T] = Nullable.empty
  def visitIfRule(node: IfRule): Nullable[T] = Nullable.empty
  def visitImportRule(node: ImportRule): Nullable[T] = Nullable.empty
  def visitIncludeRule(node: IncludeRule): Nullable[T] = Nullable.empty
  def visitLoudComment(node: LoudComment): Nullable[T] = Nullable.empty
  def visitMediaRule(node: MediaRule): Nullable[T] = Nullable.empty
  def visitMixinRule(node: MixinRule): Nullable[T] = Nullable.empty
  def visitReturnRule(node: ReturnRule): Nullable[T] = Nullable.empty
  def visitSilentComment(node: SilentComment): Nullable[T] = Nullable.empty
  def visitStyleRule(node: StyleRule): Nullable[T] = Nullable.empty
  def visitStylesheet(node: Stylesheet): Nullable[T] = Nullable.empty
  def visitSupportsRule(node: SupportsRule): Nullable[T] = Nullable.empty
  def visitUseRule(node: UseRule): Nullable[T] = Nullable.empty
  def visitVariableDeclaration(node: VariableDeclaration): Nullable[T] = Nullable.empty
  def visitWarnRule(node: WarnRule): Nullable[T] = Nullable.empty
  def visitWhileRule(node: WhileRule): Nullable[T] = Nullable.empty
}

/** A visitor that recursively traverses each selector and returns the first
  * non-empty result.
  *
  * Skeleton — defaults all returns to [[Nullable.empty]].
  */
trait SelectorSearchVisitor[T] extends SelectorVisitor[Nullable[T]] {

  def visitAttributeSelector(attribute: AttributeSelector): Nullable[T] = Nullable.empty
  def visitClassSelector(klass: ClassSelector): Nullable[T] = Nullable.empty
  def visitComplexSelector(complex: ComplexSelector): Nullable[T] = Nullable.empty
  def visitCompoundSelector(compound: CompoundSelector): Nullable[T] = Nullable.empty
  def visitIDSelector(id: IDSelector): Nullable[T] = Nullable.empty
  def visitParentSelector(parent: ParentSelector): Nullable[T] = Nullable.empty
  def visitPlaceholderSelector(placeholder: PlaceholderSelector): Nullable[T] = Nullable.empty
  def visitPseudoSelector(pseudo: PseudoSelector): Nullable[T] = Nullable.empty
  def visitSelectorList(list: SelectorList): Nullable[T] = Nullable.empty
  def visitTypeSelector(tpe: TypeSelector): Nullable[T] = Nullable.empty
  def visitUniversalSelector(universal: UniversalSelector): Nullable[T] = Nullable.empty
}

/** A visitor that recursively traverses each statement and expression in a
  * Sass AST and returns the first non-empty result.
  *
  * Skeleton — extends [[StatementSearchVisitor]]; expression traversal will
  * be added with the evaluator port.
  */
trait AstSearchVisitor[T]
    extends StatementSearchVisitor[T]
    with ExpressionVisitor[Nullable[T]]
    with IfConditionExpressionVisitor[Nullable[T]]
    with InterpolatedSelectorVisitor[Nullable[T]] {

  // ---- ExpressionVisitor ---------------------------------------------------

  def visitBinaryOperationExpression(node: BinaryOperationExpression): Nullable[T] = Nullable.empty
  def visitBooleanExpression(node: BooleanExpression): Nullable[T] = Nullable.empty
  def visitColorExpression(node: ColorExpression): Nullable[T] = Nullable.empty
  def visitFunctionExpression(node: FunctionExpression): Nullable[T] = Nullable.empty
  def visitIfExpression(node: IfExpression): Nullable[T] = Nullable.empty
  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Nullable[T] = Nullable.empty
  def visitLegacyIfExpression(node: LegacyIfExpression): Nullable[T] = Nullable.empty
  def visitListExpression(node: ListExpression): Nullable[T] = Nullable.empty
  def visitMapExpression(node: MapExpression): Nullable[T] = Nullable.empty
  def visitNullExpression(node: NullExpression): Nullable[T] = Nullable.empty
  def visitNumberExpression(node: NumberExpression): Nullable[T] = Nullable.empty
  def visitParenthesizedExpression(node: ParenthesizedExpression): Nullable[T] = Nullable.empty
  def visitSelectorExpression(node: SelectorExpression): Nullable[T] = Nullable.empty
  def visitStringExpression(node: StringExpression): Nullable[T] = Nullable.empty
  def visitSupportsExpression(node: SupportsExpression): Nullable[T] = Nullable.empty
  def visitUnaryOperationExpression(node: UnaryOperationExpression): Nullable[T] = Nullable.empty
  def visitValueExpression(node: ValueExpression): Nullable[T] = Nullable.empty
  def visitVariableExpression(node: VariableExpression): Nullable[T] = Nullable.empty

  // ---- IfConditionExpressionVisitor ---------------------------------------

  def visitIfConditionParenthesized(node: IfConditionParenthesized): Nullable[T] = Nullable.empty
  def visitIfConditionNegation(node: IfConditionNegation): Nullable[T] = Nullable.empty
  def visitIfConditionOperation(node: IfConditionOperation): Nullable[T] = Nullable.empty
  def visitIfConditionFunction(node: IfConditionFunction): Nullable[T] = Nullable.empty
  def visitIfConditionSass(node: IfConditionSass): Nullable[T] = Nullable.empty
  def visitIfConditionRaw(node: IfConditionRaw): Nullable[T] = Nullable.empty

  // ---- InterpolatedSelectorVisitor ----------------------------------------

  def visitSelectorList(node: InterpolatedSelectorList): Nullable[T] = Nullable.empty
  def visitComplexSelector(node: InterpolatedComplexSelector): Nullable[T] = Nullable.empty
  def visitCompoundSelector(node: InterpolatedCompoundSelector): Nullable[T] = Nullable.empty
  def visitAttributeSelector(node: InterpolatedAttributeSelector): Nullable[T] = Nullable.empty
  def visitClassSelector(node: InterpolatedClassSelector): Nullable[T] = Nullable.empty
  def visitIDSelector(node: InterpolatedIDSelector): Nullable[T] = Nullable.empty
  def visitParentSelector(node: InterpolatedParentSelector): Nullable[T] = Nullable.empty
  def visitPlaceholderSelector(node: InterpolatedPlaceholderSelector): Nullable[T] = Nullable.empty
  def visitPseudoSelector(node: InterpolatedPseudoSelector): Nullable[T] = Nullable.empty
  def visitTypeSelector(node: InterpolatedTypeSelector): Nullable[T] = Nullable.empty
  def visitUniversalSelector(node: InterpolatedUniversalSelector): Nullable[T] = Nullable.empty
}
