/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-superscript/src/main/java/com/vladsch/flexmark/ext/superscript/internal/SuperscriptNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package superscript
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler}
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class SuperscriptNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val superscriptStyleHtmlOpen: Nullable[String] = Nullable(SuperscriptExtension.SUPERSCRIPT_STYLE_HTML_OPEN.get(options))
  private val superscriptStyleHtmlClose: Nullable[String] = Nullable(SuperscriptExtension.SUPERSCRIPT_STYLE_HTML_CLOSE.get(options))

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set(
      new NodeRenderingHandler[Superscript](classOf[Superscript], (node, ctx, html) => render(node, ctx, html))
    ))
  }

  private def render(node: Superscript, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (superscriptStyleHtmlOpen.isEmpty || superscriptStyleHtmlClose.isEmpty) {
      if (context.getHtmlOptions.sourcePositionParagraphLines) {
        html.withAttr().tag("sup")
      } else {
        html.srcPos(node.text).withAttr().tag("sup")
      }
      context.renderChildren(node)
      html.tag("/sup")
    } else {
      html.raw(superscriptStyleHtmlOpen.get)
      context.renderChildren(node)
      html.raw(superscriptStyleHtmlClose.get)
    }
  }
}

object SuperscriptNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new SuperscriptNodeRenderer(options)
  }
}
