/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-resizable-image/src/main/java/com/vladsch/flexmark/ext/resizable/image/internal/ResizableImageNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-resizable-image/src/main/java/com/vladsch/flexmark/ext/resizable/image/internal/ResizableImageNodeRenderer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package resizable
package image
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ LinkType, NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler, ResolvedLink }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class ResizableImageNodeRenderer(options: DataHolder) extends NodeRenderer {

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set(
        new NodeRenderingHandler[ResizableImage](classOf[ResizableImage], (node, ctx, html) => render(node, ctx, html))
      )
    )

  private def render(node: ResizableImage, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (context.isDoNotRenderLinks) {
      context.renderChildren(node)
    } else {
      val link: ResolvedLink = context.resolveLink(LinkType.IMAGE, node.source, Nullable(true))
      html.srcPos(node.chars).attr("src", link.url)
      if (node.text.isNotEmpty()) {
        html.attr("alt", node.text)
      }
      if (node.width.isNotEmpty()) {
        html.attr("width", node.width.toString + "px")
      }
      if (node.height.isNotEmpty()) {
        html.attr("height", node.height.toString + "px")
      }
      html.withAttr().tag("img")
      html.tag("/img")
    }
}

object ResizableImageNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new ResizableImageNodeRenderer(options)
  }
}
