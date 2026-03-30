/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.Nullable
import ssg.md.ast.TextBase
import ssg.md.html.renderer.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AttributesNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val myOptions: AttributesOptions = new AttributesOptions(options)

  // only registered if assignTextAttributes is enabled
  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set[NodeRenderingHandler[?]](
      new NodeRenderingHandler[AttributesNode](classOf[AttributesNode], (node, context, html) => {
        // AttributesNode is NonRenderingInline - not rendered directly
      }),
      new NodeRenderingHandler[TextBase](classOf[TextBase], (node, context, html) => {
        if (myOptions.assignTextAttributes) {
          val nodeAttributes = context.extendRenderingNodeAttributes(AttributablePart.NODE, Nullable.empty)
          if (!nodeAttributes.isEmpty) {
            // has attributes then we wrap it in a span
            html.setAttributes(nodeAttributes).withAttr().tag("span")
            context.delegateRender()
            html.closeTag("span")
          } else {
            context.delegateRender()
          }
        } else {
          context.delegateRender()
        }
      })
    ))
  }
}

object AttributesNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new AttributesNodeRenderer(options)
  }
}
