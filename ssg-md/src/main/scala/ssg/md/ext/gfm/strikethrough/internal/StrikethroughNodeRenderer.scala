/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/internal/StrikethroughNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/internal/StrikethroughNodeRenderer.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package gfm
package strikethrough
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class StrikethroughNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val strikethroughStyleHtmlOpen:  Nullable[String] = Nullable(StrikethroughSubscriptExtension.STRIKETHROUGH_STYLE_HTML_OPEN.get(options))
  private val strikethroughStyleHtmlClose: Nullable[String] = Nullable(StrikethroughSubscriptExtension.STRIKETHROUGH_STYLE_HTML_CLOSE.get(options))
  private val subscriptStyleHtmlOpen:      Nullable[String] = Nullable(StrikethroughSubscriptExtension.SUBSCRIPT_STYLE_HTML_OPEN.get(options))
  private val subscriptStyleHtmlClose:     Nullable[String] = Nullable(StrikethroughSubscriptExtension.SUBSCRIPT_STYLE_HTML_CLOSE.get(options))

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    val set = scala.collection.mutable.HashSet[NodeRenderingHandler[?]]()
    set += (new NodeRenderingHandler[Strikethrough](classOf[Strikethrough], (node, ctx, html) => renderStrikethrough(node, ctx, html)))
    set.add(new NodeRenderingHandler[Subscript](classOf[Subscript], (node, ctx, html) => renderSubscript(node, ctx, html)))
    Nullable(set.toSet)
  }

  private def renderStrikethrough(node: Strikethrough, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (strikethroughStyleHtmlOpen.isEmpty || strikethroughStyleHtmlClose.isEmpty) {
      if (context.getHtmlOptions.sourcePositionParagraphLines) {
        html.withAttr().tag("del")
      } else {
        html.srcPos(node.text).withAttr().tag("del")
      }
      context.renderChildren(node)
      html.tag("/del")
    } else {
      html.raw(strikethroughStyleHtmlOpen.get)
      context.renderChildren(node)
      html.raw(strikethroughStyleHtmlClose.get)
    }

  private def renderSubscript(node: Subscript, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (subscriptStyleHtmlOpen.isEmpty || subscriptStyleHtmlClose.isEmpty) {
      if (context.getHtmlOptions.sourcePositionParagraphLines) {
        html.withAttr().tag("sub")
      } else {
        html.srcPos(node.text).withAttr().tag("sub")
      }
      context.renderChildren(node)
      html.tag("/sub")
    } else {
      html.raw(subscriptStyleHtmlOpen.get)
      context.renderChildren(node)
      html.raw(subscriptStyleHtmlClose.get)
    }
}

object StrikethroughNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new StrikethroughNodeRenderer(options)
  }
}
