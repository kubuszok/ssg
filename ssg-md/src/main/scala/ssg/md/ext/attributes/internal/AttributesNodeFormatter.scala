/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

// TODO: Full AttributesNodeFormatter port pending
class AttributesNodeFormatter(options: DataHolder) extends NodeFormatter {

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] = {
    Nullable(Set[NodeFormattingHandler[?]](
      new NodeFormattingHandler[AttributesNode](classOf[AttributesNode], (node, ctx, md) => render(node, ctx, md)),
      new NodeFormattingHandler[AttributesDelimiter](classOf[AttributesDelimiter], (node, ctx, md) => renderDelimiter(node, ctx, md))
    ))
  }

  private def render(node: AttributesNode, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    // TODO: implement full attribute formatting
    context.renderChildren(node)
    markdown.append(node.closingMarker)
  }

  private def renderDelimiter(node: AttributesDelimiter, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    markdown.append(node.closingMarker)
  }
}

object AttributesNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new AttributesNodeFormatter(options)
  }
}
