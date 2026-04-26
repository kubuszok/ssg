/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/TypographicNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/TypographicNodeRenderer.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package typographic
package internal

import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class TypographicNodeRenderer(options: DataHolder) extends NodeRenderer {

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    val set = scala.collection.mutable.HashSet[NodeRenderingHandler[?]]()
    set += (new NodeRenderingHandler[TypographicSmarts](classOf[TypographicSmarts], (node, ctx, html) => renderSmarts(node, ctx, html)))
    set.add(new NodeRenderingHandler[TypographicQuotes](classOf[TypographicQuotes], (node, ctx, html) => renderQuotes(node, ctx, html)))
    Nullable(set.toSet)
  }

  private def renderQuotes(node: TypographicQuotes, context: NodeRendererContext, html: HtmlWriter): Unit = {
    node.typographicOpening.foreach { opening =>
      if (opening.nonEmpty) html.raw(opening)
    }
    context.renderChildren(node)
    node.typographicClosing.foreach { closing =>
      if (closing.nonEmpty) html.raw(closing)
    }
  }

  private def renderSmarts(node: TypographicSmarts, context: NodeRendererContext, html: HtmlWriter): Unit =
    node.typographicText.foreach(html.raw)
}

object TypographicNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new TypographicNodeRenderer(options)
  }
}
