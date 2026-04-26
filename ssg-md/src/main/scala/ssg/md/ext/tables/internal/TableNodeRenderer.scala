/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/internal/TableNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/internal/TableNodeRenderer.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package tables
package internal

import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class TableNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val parserOptions: TableParserOptions = new TableParserOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set[NodeRenderingHandler[?]](
        new NodeRenderingHandler[TableBlock](classOf[TableBlock], (node, ctx, html) => renderBlock(node, ctx, html)),
        new NodeRenderingHandler[TableHead](classOf[TableHead], (node, ctx, html) => renderHead(node, ctx, html)),
        new NodeRenderingHandler[TableSeparator](classOf[TableSeparator], (node, ctx, html) => renderSeparator(node, ctx, html)),
        new NodeRenderingHandler[TableBody](classOf[TableBody], (node, ctx, html) => renderBody(node, ctx, html)),
        new NodeRenderingHandler[TableRow](classOf[TableRow], (node, ctx, html) => renderRow(node, ctx, html)),
        new NodeRenderingHandler[TableCell](classOf[TableCell], (node, ctx, html) => renderCell(node, ctx, html)),
        new NodeRenderingHandler[TableCaption](classOf[TableCaption], (node, ctx, html) => renderCaption(node, ctx, html))
      )
    )

  private def renderBlock(node: TableBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (!parserOptions.className.isEmpty) {
      html.attr("class", parserOptions.className)
    }
    html.srcPosWithEOL(node.chars).withAttr().tagLineIndent("table", () => context.renderChildren(node)).line()
  }

  private def renderHead(node: TableHead, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.withAttr().withCondIndent().tagLine("thead", () => context.renderChildren(node))

  private def renderSeparator(node: TableSeparator, context: NodeRendererContext, html: HtmlWriter): Unit = {}

  private def renderBody(node: TableBody, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.withAttr().withCondIndent().tagLine("tbody", () => context.renderChildren(node))

  private def renderRow(node: TableRow, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.srcPos(node.chars.trimStart()).withAttr().tagLine("tr", () => context.renderChildren(node))

  private def renderCaption(node: TableCaption, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.srcPos(node.chars.trimStart()).withAttr().tagLine("caption", () => context.renderChildren(node))

  private def renderCell(node: TableCell, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val tag = if (node.isHeader) "th" else "td"
    node.getAlignment.foreach { alignment =>
      html.attr("align", TableNodeRenderer.getAlignValue(alignment))
    }

    if (parserOptions.columnSpans && node.span > 1) {
      html.attr("colspan", String.valueOf(node.span))
    }

    html.srcPos(node.text).withAttr().tag(tag)
    context.renderChildren(node)
    html.tag("/" + tag)
  }
}

object TableNodeRenderer {

  private def getAlignValue(alignment: TableCell.Alignment): String = alignment match {
    case TableCell.Alignment.LEFT   => "left"
    case TableCell.Alignment.CENTER => "center"
    case TableCell.Alignment.RIGHT  => "right"
  }

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new TableNodeRenderer(options)
  }
}
