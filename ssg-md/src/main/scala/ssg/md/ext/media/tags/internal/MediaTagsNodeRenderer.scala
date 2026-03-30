/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/internal/MediaTagsNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package media
package tags
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{LinkType, NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler, ResolvedLink}
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class MediaTagsNodeRenderer(options: DataHolder) extends NodeRenderer {

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set(
      new NodeRenderingHandler[AudioLink](classOf[AudioLink], (node, ctx, html) => renderAudioLink(node, ctx, html)),
      new NodeRenderingHandler[EmbedLink](classOf[EmbedLink], (node, ctx, html) => renderEmbedLink(node, ctx, html)),
      new NodeRenderingHandler[PictureLink](classOf[PictureLink], (node, ctx, html) => renderPictureLink(node, ctx, html)),
      new NodeRenderingHandler[VideoLink](classOf[VideoLink], (node, ctx, html) => renderVideoLink(node, ctx, html))
    ))
  }

  private def renderAudioLink(node: AudioLink, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (context.isDoNotRenderLinks) {
      context.renderChildren(node)
    } else {
      val resolvedLink: ResolvedLink = context.resolveLink(LinkType.LINK, node.url.unescape(), Nullable(false))
      val sources = resolvedLink.url.split("\\|")
      html.attr("title", node.text)
        .attr("controls", "")
        .withAttr()
        .tag("audio")
      for (source <- sources) {
        val encoded = if (context.getHtmlOptions.percentEncodeUrls) context.encodeUrl(source) else source
        val audioType = Utilities.resolveAudioType(source)
        html.attr("src", encoded)
        if (audioType.isDefined) html.attr("type", audioType.get)
        html.withAttr().tag("source", true)
      }
      html.text("Your browser does not support the audio element.")
      html.tag("/audio")
    }
  }

  private def renderEmbedLink(node: EmbedLink, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (context.isDoNotRenderLinks) {
      context.renderChildren(node)
    } else {
      val resolvedLink: ResolvedLink = context.resolveLink(LinkType.LINK, node.url.unescape(), Nullable.empty)

      html.attr("title", node.text)
        .attr("src", resolvedLink.url)
        .withAttr()
        .tag("embed", true)
    }
  }

  private def renderPictureLink(node: PictureLink, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (context.isDoNotRenderLinks) {
      context.renderChildren(node)
    } else {
      val resolvedLink: ResolvedLink = context.resolveLink(LinkType.LINK, node.url.unescape(), Nullable(false))
      val sources = resolvedLink.url.split("\\|")
      html.tag("picture")
      for (index <- 0 until sources.length - 1) {
        val source = sources(index)
        val encoded = if (context.getHtmlOptions.percentEncodeUrls) context.encodeUrl(source) else source
        html.attr("srcset", encoded)
          .withAttr()
          .tag("source", true)
      }
      val last = sources.length - 1
      if (last >= 0) {
        val source = sources(last)
        val encoded = if (context.getHtmlOptions.percentEncodeUrls) context.encodeUrl(source) else source
        html.attr("src", encoded)
          .attr("alt", node.text)
          .withAttr()
          .tag("img", true)
      }
      html.tag("/picture")
    }
  }

  private def renderVideoLink(node: VideoLink, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (context.isDoNotRenderLinks) {
      context.renderChildren(node)
    } else {
      val resolvedLink: ResolvedLink = context.resolveLink(LinkType.LINK, node.url.unescape(), Nullable(false))
      val sources = resolvedLink.url.split("\\|")
      html.attr("title", node.text)
        .attr("controls", "")
        .withAttr()
        .tag("video")
      for (source <- sources) {
        val encoded = if (context.getHtmlOptions.percentEncodeUrls) context.encodeUrl(source) else source
        val videoType = Utilities.resolveVideoType(source)
        html.attr("src", encoded)
        if (videoType.isDefined) html.attr("type", videoType.get)
        html.withAttr().tag("source", true)
      }
      html.text("Your browser does not support the video element.")
      html.tag("/video")
    }
  }
}

object MediaTagsNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new MediaTagsNodeRenderer(options)
  }
}
