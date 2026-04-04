/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/SimTocNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.Nullable
import ssg.md.ast.Heading
import ssg.md.ast.util.HeadingCollectingVisitor
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.util.ast.Node
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }
import scala.language.implicitConversions

class SimTocNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val tocOptions: TocOptions = TocOptions.fromOptions(options, true)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set[NodeRenderingHandler[?]](
        new NodeRenderingHandler[SimTocBlock](classOf[SimTocBlock], (node, ctx, html) => renderSimTocBlock(node, ctx, html)),
        new NodeRenderingHandler[SimTocContent](classOf[SimTocContent], (node, ctx, html) => renderSimTocContent(node, ctx, html)),
        new NodeRenderingHandler[SimTocOptionList](classOf[SimTocOptionList], (node, ctx, html) => renderSimTocOptionList(node, ctx, html)),
        new NodeRenderingHandler[SimTocOption](classOf[SimTocOption], (node, ctx, html) => renderSimTocOption(node, ctx, html))
      )
    )

  // we don't render these or their children
  private def renderSimTocContent(node:    SimTocContent, context:    NodeRendererContext, html: HtmlWriter): Unit = {}
  private def renderSimTocOptionList(node: SimTocOptionList, context: NodeRendererContext, html: HtmlWriter): Unit = {}
  private def renderSimTocOption(node:     SimTocOption, context:     NodeRendererContext, html: HtmlWriter): Unit = {}

  private def renderSimTocBlock(node: SimTocBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val visitor  = new HeadingCollectingVisitor()
    val headings = visitor.collectAndGetHeadings(context.getDocument)
    if (headings != null) { // @nowarn - Java interop: may be null
      val optionsParser = new SimTocOptionsParser()
      var options       = optionsParser.parseOption(node.style, tocOptions, Nullable.empty).first.get

      if (node.title ne BasedSequence.NULL) {
        options = options.withTitle(node.title.unescape())
      }
      renderTocHeaders(context, html, node, headings, options)
    }
  }

  private def renderTocHeaders(context: NodeRendererContext, html: HtmlWriter, node: Node, headings: ju.List[Heading], options: TocOptions): Unit = {
    val filteredHeadings = TocUtils.filteredHeadings(headings, options)
    val paired           = TocUtils.htmlHeadingTexts(context, filteredHeadings, options)
    val resultHeadings   = paired.first.get
    val resultTexts      = paired.second.get
    val headingLevels    = new ju.ArrayList[Integer](resultHeadings.size())
    val headingRefIds    = new ju.ArrayList[String](resultHeadings.size())
    val it               = resultHeadings.iterator()
    while (it.hasNext) {
      val h = it.next()
      headingLevels.add(h.level)
      headingRefIds.add(h.anchorRefId)
    }
    TocUtils.renderHtmlToc(
      html,
      if (context.getHtmlOptions.sourcePositionAttribute.isEmpty) BasedSequence.NULL else node.chars,
      headingLevels,
      resultTexts,
      headingRefIds,
      options
    )
  }
}

object SimTocNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new SimTocNodeRenderer(options)
  }
}
