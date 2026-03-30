/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocNodeRenderer.java
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

import java.{util => ju}
import scala.language.implicitConversions

class TocNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val haveTitle: Boolean = options != null && options.contains(TocExtension.TITLE) // @nowarn - Java interop: may be null
  private val tocOptions: TocOptions = TocOptions.fromOptions(options, false)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set[NodeRenderingHandler[?]](
      new NodeRenderingHandler[TocBlock](classOf[TocBlock], (node, ctx, html) => render(node, ctx, html))
    ))
  }

  private def render(node: TocBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val visitor = new HeadingCollectingVisitor()
    val headings = visitor.collectAndGetHeadings(context.getDocument)
    if (headings != null) { // @nowarn - Java interop: may be null
      val optionsParser = new TocOptionsParser()
      val titleOptions = if (haveTitle) tocOptions else tocOptions.withTitle("")
      val parsed = optionsParser.parseOption(node.style, titleOptions, Nullable.empty)
      val opts = parsed.first.get
      renderTocHeaders(context, html, node, headings, opts)
    }
  }

  private def renderTocHeaders(context: NodeRendererContext, html: HtmlWriter, node: Node, headings: ju.List[Heading], options: TocOptions): Unit = {
    val filteredHeadings = TocUtils.filteredHeadings(headings, options)
    val paired = TocUtils.htmlHeadingTexts(context, filteredHeadings, options)
    val resultHeadings = paired.first.get
    val resultTexts = paired.second.get
    val headingLevels = new ju.ArrayList[Integer](resultHeadings.size())
    val headingRefIds = new ju.ArrayList[String](resultHeadings.size())
    val it = resultHeadings.iterator()
    while (it.hasNext) {
      val h = it.next()
      headingLevels.add(h.level)
      headingRefIds.add(h.anchorRefId)
    }
    TocUtils.renderHtmlToc(html, if (context.getHtmlOptions.sourcePositionAttribute.isEmpty) BasedSequence.NULL else node.chars, headingLevels, resultTexts, headingRefIds, options)
  }
}

object TocNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new TocNodeRenderer(options)
  }
}
