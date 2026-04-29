/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagNodeRenderer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package jekyll
package tag
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions
import java.util.Map as JMap

class JekyllTagNodeRenderer(options: DataHolder) extends NodeRenderer {

  @annotation.nowarn("msg=unused private member")
  private val includeContent: Nullable[JMap[String, String]] = Nullable(JekyllTagExtension.INCLUDED_HTML.get(options))
  private val embedIncludes:  Boolean                        = JekyllTagExtension.EMBED_INCLUDED_CONTENT.get(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    val set = scala.collection.mutable.HashSet[NodeRenderingHandler[?]]()
    set += (new NodeRenderingHandler[JekyllTag](classOf[JekyllTag], (node, context, html) => renderTag(node, context, html)))
    set.add(new NodeRenderingHandler[JekyllTagBlock](classOf[JekyllTagBlock], (node, context, html) => renderBlock(node, context, html)))
    Nullable(set.toSet)
  }

  private def renderTag(node: JekyllTag, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (embedIncludes) {
      context.renderChildren(node)
    } else {
      // nothing to do since includes are not rendered
    }

  private def renderBlock(node: JekyllTagBlock, context: NodeRendererContext, html: HtmlWriter): Unit =
    context.renderChildren(node)
}

object JekyllTagNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new JekyllTagNodeRenderer(options)
  }
}
