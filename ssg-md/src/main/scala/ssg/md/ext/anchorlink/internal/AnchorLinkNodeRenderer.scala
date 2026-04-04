/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-anchorlink/src/main/java/com/vladsch/flexmark/ext/anchorlink/internal/AnchorLinkNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package anchorlink
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AnchorLinkNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val anchorLinkOptions = new AnchorLinkOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set(
        new NodeRenderingHandler[AnchorLink](classOf[AnchorLink], (node, ctx, html) => render(node, ctx, html))
      )
    )

  private def render(node: AnchorLink, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (context.isDoNotRenderLinks) {
      if (anchorLinkOptions.wrapText) {
        context.renderChildren(node)
      }
    } else {
      val id = context.getNodeId(node.parent.get)
      if (id.isDefined) {
        val idVal = id.get
        html.attr("href", "#" + idVal)
        if (anchorLinkOptions.setId) html.attr("id", idVal)
        if (anchorLinkOptions.setName) html.attr("name", idVal)
        if (anchorLinkOptions.anchorClass.nonEmpty) html.attr("class", anchorLinkOptions.anchorClass)

        if (!anchorLinkOptions.wrapText) {
          html.withAttr().tag("a")
          if (anchorLinkOptions.textPrefix.nonEmpty) html.raw(anchorLinkOptions.textPrefix)
          if (anchorLinkOptions.textSuffix.nonEmpty) html.raw(anchorLinkOptions.textSuffix)
          html.tag("/a")
        } else {
          html
            .withAttr()
            .tag(
              "a",
              false,
              false,
              () => {
                if (anchorLinkOptions.textPrefix.nonEmpty) html.raw(anchorLinkOptions.textPrefix)
                context.renderChildren(node)
                if (anchorLinkOptions.textSuffix.nonEmpty) html.raw(anchorLinkOptions.textSuffix)
              }
            )
        }
      } else {
        if (anchorLinkOptions.wrapText) {
          context.renderChildren(node)
        }
      }
    }
}

object AnchorLinkNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new AnchorLinkNodeRenderer(options)
  }
}
