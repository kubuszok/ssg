/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/AllNodesVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

abstract class AllNodesVisitor {
  protected def process(node: Node): Unit

  def visit(node: Node): Unit =
    visitChildren(node)

  private def visitChildren(parent: Node): Unit = {
    var node = parent.firstChild
    while (node.isDefined) {
      // A subclass of this visitor might modify the node, resulting in getNext returning a different node or no
      // node after visiting it. So get the next node before visiting.
      val next = node.get.next
      process(node.get)
      visit(node.get)
      node = next
    }
  }
}
