/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/internal/JekyllFrontMatterNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package jekyll
package front
package matter
package internal

import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class JekyllFrontMatterNodeRenderer(options: DataHolder) extends NodeRenderer {

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    val set = scala.collection.mutable.HashSet[NodeRenderingHandler[?]]()
    set += (new NodeRenderingHandler[JekyllFrontMatterBlock](classOf[JekyllFrontMatterBlock], (node, context, html) => render(node, context, html)))
    Nullable(set.toSet)
  }

  private def render(node: JekyllFrontMatterBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {}
}

object JekyllFrontMatterNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new JekyllFrontMatterNodeRenderer(options)
  }
}
