/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package definition
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.parser.ListOptions
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class DefinitionNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val listOptions: ListOptions = ListOptions.get(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set[NodeRenderingHandler[?]](
      new NodeRenderingHandler[DefinitionList](classOf[DefinitionList], (node, ctx, html) => renderList(node, ctx, html)),
      new NodeRenderingHandler[DefinitionTerm](classOf[DefinitionTerm], (node, ctx, html) => renderTerm(node, ctx, html)),
      new NodeRenderingHandler[DefinitionItem](classOf[DefinitionItem], (node, ctx, html) => renderItem(node, ctx, html))
    ))
  }

  private def renderList(node: DefinitionList, context: NodeRendererContext, html: HtmlWriter): Unit = {
    html.withAttr().tag("dl").indent()
    context.renderChildren(node)
    html.unIndent()
    html.tag("/dl")
  }

  private def renderTerm(node: DefinitionTerm, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val childText = node.firstChild
    if (childText.isDefined) {
      html.srcPosWithEOL(node.chars).withAttr(CoreNodeRenderer.TIGHT_LIST_ITEM).withCondIndent().tagLine("dt", () => {
        html.text(node.markerSuffix.unescape())
        context.renderChildren(node)
      })
    }
  }

  private def renderItem(node: DefinitionItem, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (listOptions.isTightListItem(node)) {
      html.srcPosWithEOL(node.chars).withAttr(CoreNodeRenderer.TIGHT_LIST_ITEM).withCondIndent().tagLine("dd", () => {
        html.text(node.markerSuffix.unescape())
        context.renderChildren(node)
      })
    } else {
      html.srcPosWithEOL(node.chars).withAttr(CoreNodeRenderer.LOOSE_LIST_ITEM).tagIndent("dd", () => {
        html.text(node.markerSuffix.unescape())
        context.renderChildren(node)
      })
    }
  }
}

object DefinitionNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new DefinitionNodeRenderer(options)
  }
}
