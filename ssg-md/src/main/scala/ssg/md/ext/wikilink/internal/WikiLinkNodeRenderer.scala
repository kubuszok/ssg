/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/internal/WikiLinkNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/internal/WikiLinkNodeRenderer.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package wikilink
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class WikiLinkNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val wikiOptions: WikiLinkOptions = new WikiLinkOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set[NodeRenderingHandler[?]](
        new NodeRenderingHandler[WikiLink](classOf[WikiLink], (node, ctx, html) => renderWikiLink(node, ctx, html)),
        new NodeRenderingHandler[WikiImage](classOf[WikiImage], (node, ctx, html) => renderWikiImage(node, ctx, html))
      )
    )

  private def renderWikiLink(node: WikiLink, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (!context.isDoNotRenderLinks) {
      if (wikiOptions.disableRendering) {
        html.text(node.chars.unescape())
      } else {
        val resolvedLink = context.resolveLink(WikiLinkExtension.WIKI_LINK, node.link, Nullable.empty)
        html.attr("href", resolvedLink.url)
        html.srcPos(node.chars).withAttr(resolvedLink).tag("a")
        context.renderChildren(node)
        html.tag("/a")
      }
    }

  private def renderWikiImage(node: WikiImage, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (!context.isDoNotRenderLinks) {
      if (wikiOptions.disableRendering) {
        html.text(node.chars.unescape())
      } else {
        val altText = if (node.text.isNotNull) node.text.toString else node.link.unescape()

        val resolvedLink = context.resolveLink(WikiLinkExtension.WIKI_LINK, node.link, Nullable.empty)
        val url          = resolvedLink.url

        html.attr("src", url)
        html.attr("alt", altText)
        html.srcPos(node.chars).withAttr(resolvedLink).tagVoid("img")
      }
    }
}

object WikiLinkNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new WikiLinkNodeRenderer(options)
  }
}
