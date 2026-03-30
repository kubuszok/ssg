/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package abbreviation
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AbbreviationNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val myOptions: AbbreviationOptions = new AbbreviationOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set[NodeRenderingHandler[?]](
      new NodeRenderingHandler[Abbreviation](classOf[Abbreviation], (node, ctx, html) => renderAbbreviation(node, ctx, html)),
      new NodeRenderingHandler[AbbreviationBlock](classOf[AbbreviationBlock], (node, ctx, html) => renderAbbreviationBlock(node, ctx, html))
    ))
  }

  private def renderAbbreviationBlock(node: AbbreviationBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    // not rendered
  }

  private def renderAbbreviation(node: Abbreviation, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val text = node.chars.unescape()
    val abbreviation = node.abbreviation
    val tag: String = if (myOptions.useLinks) {
      html.attr("href", "#")
      "a"
    } else {
      "abbr"
    }

    html.attr("title", abbreviation)
    html.srcPos(node.chars).withAttr().tag(tag)
    html.text(text)
    html.closeTag(tag)
  }
}

object AbbreviationNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new AbbreviationNodeRenderer(options)
  }
}
