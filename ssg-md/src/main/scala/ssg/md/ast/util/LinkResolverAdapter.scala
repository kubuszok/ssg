/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/LinkResolverAdapter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast
package util

import ssg.md.html.renderer.LinkResolverBasicContext
import ssg.md.html.renderer.ResolvedLink
import ssg.md.util.ast.Node
import ssg.md.util.visitor.AstActionHandler

import java.{ util => ju }

class LinkResolverAdapter(handlers: LinkResolvingHandler[? <: Node]*)
    extends AstActionHandler[LinkResolverAdapter, Node, LinkResolvingHandler.LinkResolvingVisitor[Node], LinkResolvingHandler[Node]](Node.AST_ADAPTER),
    LinkResolvingHandler.LinkResolvingVisitor[Node] {

  addActionHandlers(handlers.toArray.asInstanceOf[Array[LinkResolvingHandler[Node]]])

  def this(handlers: ju.Collection[LinkResolvingHandler[? <: Node]]) = {
    this()
    addHandlers(handlers)
  }

  def addHandlers(handlers: ju.Collection[LinkResolvingHandler[? <: Node]]): LinkResolverAdapter = {
    val arr = handlers.toArray(LinkResolverAdapter.EMPTY_HANDLERS)
    addActionHandlers(arr.asInstanceOf[Array[LinkResolvingHandler[Node]]])
    this
  }

  def addHandlers(handlers: Array[LinkResolvingHandler[? <: Node]]): LinkResolverAdapter = {
    addActionHandlers(handlers.asInstanceOf[Array[LinkResolvingHandler[Node]]])
    this
  }

  def addHandler(handler: LinkResolvingHandler[? <: Node]): LinkResolverAdapter = {
    addActionHandler(handler.asInstanceOf[LinkResolvingHandler[Node]])
    this
  }

  override def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink =
    processNodeOnly(node, link, (n, handler) => handler.resolveLink(n, context, link))
}

object LinkResolverAdapter {
  private val EMPTY_HANDLERS: Array[LinkResolvingHandler[? <: Node]] = Array.empty
}
