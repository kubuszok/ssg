/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-youtube-embedded/src/main/java/com/vladsch/flexmark/ext/youtube/embedded/internal/YouTubeLinkNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-youtube-embedded/src/main/java/com/vladsch/flexmark/ext/youtube/embedded/internal/YouTubeLinkNodeRenderer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package youtube
package embedded
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ LinkType, NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler, ResolvedLink }
import ssg.md.util.data.DataHolder

import java.net.URI
import scala.language.implicitConversions

class YouTubeLinkNodeRenderer(options: DataHolder) extends NodeRenderer {

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set(
        new NodeRenderingHandler[YouTubeLink](classOf[YouTubeLink], (node, ctx, html) => render(node, ctx, html))
      )
    )

  private def render(node: YouTubeLink, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (context.isDoNotRenderLinks) {
      context.renderChildren(node)
    } else {
      // standard Link Rendering
      val resolvedLink: ResolvedLink = context.resolveLink(LinkType.LINK, node.url.unescape(), Nullable.empty)

      var uri: Nullable[URI] = Nullable.empty
      try
        uri = Nullable(new URI(resolvedLink.url))
      catch {
        case _: Exception => // ignore malformed URIs
      }

      val uriHost = uri.map(u => Nullable(u.getHost)).getOrElse(Nullable.empty)
      val uriPath = uri
        .map { u =>
          val path  = Nullable(u.getRawPath).getOrElse("")
          val query = Nullable(u.getRawQuery)
          if (query.isDefined) path + "?" + query.get else path
        }
        .getOrElse("")

      if (uriHost.isDefined && "youtu.be".equalsIgnoreCase(uriHost.get)) {
        html.attr("src", "https://www.youtube-nocookie.com/embed" + uriPath.replace("?t=", "?start="))
        html.attr("width", "560")
        html.attr("height", "315")
        html.attr("class", "youtube-embedded")
        html.attr("allowfullscreen", "true")
        html.attr("frameborder", "0")
        html.attr("allow", "accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture")
        html.srcPos(node.chars).withAttr(resolvedLink).tag("iframe")
        html.tag("/iframe")
      } else if (resolvedLink.url.contains("www.youtube.com/watch")) {
        html.attr("src", resolvedLink.url.replace("watch?v=".toLowerCase, "embed/"))
        html.attr("width", "420")
        html.attr("height", "315")
        html.attr("class", "youtube-embedded")
        html.attr("allowfullscreen", "true")
        html.attr("frameborder", "0")
        html.srcPos(node.chars).withAttr(resolvedLink).tag("iframe")
        // context.renderChildren(node)
        html.tag("/iframe")
      } else {
        html.attr("href", resolvedLink.url)
        if (node.title.isNotNull) {
          html.attr("title", node.title.unescape())
        }
        html.srcPos(node.chars).withAttr(resolvedLink).tag("a")
        context.renderChildren(node)
        html.tag("/a")
      }
    }
}

object YouTubeLinkNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new YouTubeLinkNodeRenderer(options)
  }
}
