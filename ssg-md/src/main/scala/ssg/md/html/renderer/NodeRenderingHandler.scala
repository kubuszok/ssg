/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/NodeRenderingHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/NodeRenderingHandler.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html
package renderer

import ssg.md.util.ast.Node
import ssg.md.util.visitor.AstHandler

class NodeRenderingHandler[N <: Node](aClass: Class[N], adapter: NodeRenderingHandler.CustomNodeRenderer[N]) extends AstHandler[N, NodeRenderingHandler.CustomNodeRenderer[N]](aClass, adapter) {

  def render(node: Node, context: NodeRendererContext, html: HtmlWriter): Unit =
    adapter.render(node.asInstanceOf[N], context, html)
}

object NodeRenderingHandler {
  trait CustomNodeRenderer[N <: Node] extends ssg.md.util.visitor.AstAction[N] {
    def render(node: N, context: NodeRendererContext, html: HtmlWriter): Unit
  }
}
