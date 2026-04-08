/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/recursive_statement.dart
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: recursive_statement.dart -> RecursiveStatementVisitor.scala
 *   Convention: Dart mixin -> Scala trait with default impls
 *   Idiom: Returns Unit; default behavior is to recurse into children
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.*

/** A visitor that recursively traverses each statement in a Sass AST.
  *
  * In addition to the methods from [[StatementVisitor]], this has more general methods that can be overridden to add behavior for a wide variety of AST nodes:
  *
  *   - [[visitCallableDeclaration]]
  *   - [[visitChildren]]
  */
trait RecursiveStatementVisitor extends StatementVisitor[Unit] {

  def visitAtRootRule(node: AtRootRule): Unit =
    visitChildren(node.children.get)

  def visitAtRule(node: AtRule): Unit =
    node.children.foreach(visitChildren)

  def visitContentBlock(node: ContentBlock): Unit =
    visitCallableDeclaration(node)

  def visitContentRule(node: ContentRule): Unit = ()

  def visitDebugRule(node: DebugRule): Unit = ()

  def visitDeclaration(node: Declaration): Unit =
    node.children.foreach(visitChildren)

  def visitEachRule(node: EachRule): Unit =
    visitChildren(node.children.get)

  def visitErrorRule(node: ErrorRule): Unit = ()

  def visitExtendRule(node: ExtendRule): Unit = ()

  def visitForRule(node: ForRule): Unit =
    visitChildren(node.children.get)

  def visitForwardRule(node: ForwardRule): Unit = ()

  def visitFunctionRule(node: FunctionRule): Unit =
    visitCallableDeclaration(node)

  def visitIfRule(node: IfRule): Unit = {
    for (clause <- node.clauses)
      for (child <- clause.children)
        child.accept(this)
    node.lastClause.foreach { lastClause =>
      for (child <- lastClause.children)
        child.accept(this)
    }
  }

  def visitImportRule(node: ImportRule): Unit = ()

  def visitIncludeRule(node: IncludeRule): Unit =
    node.content.foreach(visitContentBlock)

  def visitLoudComment(node: LoudComment): Unit = ()

  def visitMediaRule(node: MediaRule): Unit =
    visitChildren(node.children.get)

  def visitMixinRule(node: MixinRule): Unit =
    visitCallableDeclaration(node)

  def visitReturnRule(node: ReturnRule): Unit = ()

  def visitSilentComment(node: SilentComment): Unit = ()

  def visitStyleRule(node: StyleRule): Unit =
    visitChildren(node.children.get)

  def visitStylesheet(node: Stylesheet): Unit =
    visitChildren(node.children.get)

  def visitSupportsRule(node: SupportsRule): Unit =
    visitChildren(node.children.get)

  def visitUseRule(node: UseRule): Unit = ()

  def visitVariableDeclaration(node: VariableDeclaration): Unit = ()

  def visitWarnRule(node: WarnRule): Unit = ()

  def visitWhileRule(node: WhileRule): Unit =
    visitChildren(node.children.get)

  /** Visits each of [[node]]'s children.
    *
    * The default implementations of [[visitFunctionRule]] and [[visitMixinRule]] call this.
    */
  protected def visitCallableDeclaration(node: CallableDeclaration): Unit =
    visitChildren(node.childrenList)

  /** Visits each child in [[children]].
    *
    * The default implementation of the visit methods for all `ParentStatement`s call this.
    */
  protected def visitChildren(children: List[Statement]): Unit =
    for (child <- children)
      child.accept(this)
}
