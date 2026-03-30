/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-ins/src/main/java/com/vladsch/flexmark/ext/ins/internal/InsNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package ins
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler}
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class InsNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val insStyleHtmlOpen: Nullable[String] = Nullable(InsExtension.INS_STYLE_HTML_OPEN.get(options))
  private val insStyleHtmlClose: Nullable[String] = Nullable(InsExtension.INS_STYLE_HTML_CLOSE.get(options))

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set(
      new NodeRenderingHandler[Ins](classOf[Ins], (node, ctx, html) => render(node, ctx, html))
    ))
  }

  private def render(node: Ins, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (insStyleHtmlOpen.isEmpty || insStyleHtmlClose.isEmpty) {
      if (context.getHtmlOptions.sourcePositionParagraphLines) {
        html.withAttr().tag("ins")
      } else {
        html.srcPos(node.text).withAttr().tag("ins")
      }
      context.renderChildren(node)
      html.tag("/ins")
    } else {
      html.raw(insStyleHtmlOpen.get)
      context.renderChildren(node)
      html.raw(insStyleHtmlClose.get)
    }
  }
}

object InsNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new InsNodeRenderer(options)
  }
}
