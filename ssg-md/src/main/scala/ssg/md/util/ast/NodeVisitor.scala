/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.util.visitor.AstActionHandler

/*
 * Configurable node visitor handler which does not know anything about node subclasses
 * while allowing easy configuration of custom visitor for nodes of interest to visit.
 *
 * Usage:
 *
 * myVisitor = new NodeVisitor(
 *     new VisitHandler(classOf[Document], this.visit),
 *     new VisitHandler(classOf[HtmlEntity], this.visit),
 *     new VisitHandler(classOf[SoftLineBreak], this.visit),
 *     new VisitHandler(classOf[HardLineBreak], this.visit)
 * )
 *
 * Document doc;
 * myVisitor.visit(doc)
 *
 * NOTE: This class replaces the old NodeVisitor derived from NodeAdaptedVisitor.
 *
 * If you were overriding visit(Node) to provide your own handling of child visits, in the current implementation,
 * it only starts the visiting process and is no longer called for processing every child node.
 */
class NodeVisitor extends AstActionHandler[NodeVisitor, Node, Visitor[Node], VisitHandler[Node]](Node.AST_ADAPTER), NodeVisitHandler {

  def this(handlers: VisitHandler[?]*) = {
    this()
    super.addActionHandlers(handlers.toArray.asInstanceOf[Array[VisitHandler[Node]]])
  }

  // NOTE: the original Java had a constructor taking VisitHandler[]... (array of arrays).
  // In Scala, varargs of Array[T] and T both erase to Seq, so this is provided as a regular method instead.
  // Use addHandlerArrays() for the array-of-arrays case.

  def this(handlers: java.util.Collection[VisitHandler[?]]) = {
    this()
    addHandlers(handlers)
  }

  // add handler variations
  def addTypedHandlers(handlers: java.util.Collection[VisitHandler[?]]): NodeVisitor = {
    val arr = handlers.toArray(NodeVisitor.EMPTY_HANDLERS).asInstanceOf[Array[VisitHandler[Node]]]
    super.addActionHandlers(arr)
  }

  def addHandlers(handlers: java.util.Collection[VisitHandler[?]]): NodeVisitor = {
    val arr = handlers.toArray(NodeVisitor.EMPTY_HANDLERS).asInstanceOf[Array[VisitHandler[Node]]]
    super.addActionHandlers(arr)
  }

  def addHandlers(handlers: Array[VisitHandler[?]]): NodeVisitor =
    super.addActionHandlers(handlers.asInstanceOf[Array[VisitHandler[Node]]])

  def addHandlerArrays(handlers: Array[VisitHandler[?]]*): NodeVisitor = {
    for (h <- handlers)
      super.addActionHandlers(h.asInstanceOf[Array[VisitHandler[Node]]])
    this
  }

  def addHandler(handler: VisitHandler[?]): NodeVisitor =
    super.addActionHandler(handler.asInstanceOf[VisitHandler[Node]])

  final override def visit(node: Node): Unit =
    processNode(node, true, (n: Node, v: Visitor[Node]) => v.visit(n))

  final override def visitNodeOnly(node: Node): Unit =
    processNode(node, false, (n: Node, v: Visitor[Node]) => v.visit(n))

  final override def visitChildren(parent: Node): Unit =
    processChildren(parent, (n: Node, v: Visitor[Node]) => v.visit(n))
}

object NodeVisitor {
  protected val EMPTY_HANDLERS: Array[VisitHandler[?]] = Array.empty[VisitHandler[?]]
}
