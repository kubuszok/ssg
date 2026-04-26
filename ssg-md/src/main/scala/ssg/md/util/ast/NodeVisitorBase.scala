/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeVisitorBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeVisitorBase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

/** Abstract visitor that visits all children by default.
  *
  * Can be used to only process certain nodes. If you override a method and want visiting to descend into children, call visitChildren.
  */
abstract class NodeVisitorBase {
  protected def visit(node: Node): Unit

  def visitChildren(parent: Node): Unit = {
    var node = parent.firstChild
    while (node.isDefined) {
      // A subclass of this visitor might modify the node, resulting in getNext returning a different node or no
      // node after visiting it. So get the next node before visiting.
      val next = node.get.next
      visit(node.get)
      node = next
    }
  }
}
