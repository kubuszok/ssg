/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/internal/AsideNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package aside
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler}
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AsideNodeRenderer(options: DataHolder) extends NodeRenderer {

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set(
      new NodeRenderingHandler[AsideBlock](classOf[AsideBlock], (node, ctx, html) => render(node, ctx, html))
    ))
  }

  private def render(node: AsideBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    html.withAttr().withCondIndent().tagLine("aside", () => context.renderChildren(node))
  }
}

object AsideNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new AsideNodeRenderer(options)
  }
}
