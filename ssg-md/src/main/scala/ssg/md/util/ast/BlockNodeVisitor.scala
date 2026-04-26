/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlockNodeVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlockNodeVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

/** Used to visit only block nodes, non block nodes or children of non-block nodes are not visited
  *
  * Can be used to only process certain nodes. If you override a method and want visiting to descend into children, call visitChildren.
  */
class BlockNodeVisitor extends NodeVisitor {

  def this(handlers: VisitHandler[?]*) = {
    this()
    for (h <- handlers)
      addHandler(h)
  }

  def this(handlers: java.util.Collection[VisitHandler[?]]) = {
    this()
    addHandlers(handlers)
  }

  override def processNode(node: Node, withChildren: Boolean, processor: (Node, Visitor[Node]) => Unit): Unit =
    if (node.isInstanceOf[Block]) {
      super.processNode(node, withChildren, processor)
    }
}
