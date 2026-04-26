/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/AttributeProviderAdapter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/AttributeProviderAdapter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast
package util

import ssg.md.html.renderer.AttributablePart
import ssg.md.util.ast.Node
import ssg.md.util.html.MutableAttributes
import ssg.md.util.visitor.AstActionHandler

import java.{ util => ju }

class AttributeProviderAdapter(handlers: AttributeProvidingHandler[? <: Node]*)
    extends AstActionHandler[
      AttributeProviderAdapter,
      Node,
      AttributeProvidingHandler.AttributeProvidingVisitor[Node],
      AttributeProvidingHandler[Node]
    ](Node.AST_ADAPTER),
      AttributeProvidingHandler.AttributeProvidingVisitor[Node] {

  addActionHandlers(handlers.toArray.asInstanceOf[Array[AttributeProvidingHandler[Node]]])

  def this(handlers: ju.Collection[AttributeProvidingHandler[? <: Node]]) = {
    this()
    addHandlers(handlers)
  }

  def addHandlers(handlers: ju.Collection[AttributeProvidingHandler[? <: Node]]): AttributeProviderAdapter = {
    val arr = handlers.toArray(AttributeProviderAdapter.EMPTY_HANDLERS)
    addActionHandlers(arr.asInstanceOf[Array[AttributeProvidingHandler[Node]]])
    this
  }

  def addHandler(handler: AttributeProvidingHandler[? <: Node]): AttributeProviderAdapter = {
    addActionHandler(handler.asInstanceOf[AttributeProvidingHandler[Node]])
    this
  }

  override def setAttributes(node: Node, part: AttributablePart, attributes: MutableAttributes): Unit =
    processNode(node, false, (n, handler) => handler.setAttributes(n, part, attributes))
}

object AttributeProviderAdapter {
  private val EMPTY_HANDLERS: Array[AttributeProvidingHandler[? <: Node]] = Array.empty
}
