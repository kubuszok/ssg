/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/internal/FootnoteNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.util.ast.{Document, NodeVisitor, VisitHandler}
import ssg.md.util.data.DataHolder

import java.util.Locale
import scala.language.implicitConversions

class FootnoteNodeRenderer(options: DataHolder) extends PhasedNodeRenderer {

  private val footnoteRepository: FootnoteRepository = FootnoteExtension.FOOTNOTES.get(options)
  private val footnoteOptions: FootnoteOptions = new FootnoteOptions(options)
  private val recheckUndefinedReferences: Boolean = HtmlRenderer.RECHECK_UNDEFINED_REFERENCES.get(options)
  footnoteRepository.resolveFootnoteOrdinals()

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set[NodeRenderingHandler[?]](
      new NodeRenderingHandler[Footnote](classOf[Footnote], (node, ctx, html) => renderFootnote(node, ctx, html)),
      new NodeRenderingHandler[FootnoteBlock](classOf[FootnoteBlock], (node, ctx, html) => renderFootnoteBlock(node, ctx, html))
    ))
  }

  override def getRenderingPhases: Nullable[Set[RenderingPhase]] = {
    Nullable(Set(RenderingPhase.BODY_TOP, RenderingPhase.BODY_BOTTOM))
  }

  override def renderDocument(context: NodeRendererContext, html: HtmlWriter, document: Document, phase: RenderingPhase): Unit = {
    if (phase == RenderingPhase.BODY_TOP) {
      if (recheckUndefinedReferences) {
        // need to see if have undefined footnotes that were defined after parsing
        var hadNewFootnotes = false
        val visitor = new NodeVisitor(
          new VisitHandler[Footnote](classOf[Footnote], node => {
            if (!node.isDefined) {
              val footonoteBlock = node.getFootnoteBlock(footnoteRepository)

              if (footonoteBlock != null) { // @nowarn - Java interop: may be null
                footnoteRepository.addFootnoteReference(footonoteBlock, node)
                node.footnoteBlock = Nullable(footonoteBlock)
                hadNewFootnotes = true
              }
            }
          })
        )

        visitor.visit(document)
        if (hadNewFootnotes) {
          this.footnoteRepository.resolveFootnoteOrdinals()
        }
      }
    }

    if (phase == RenderingPhase.BODY_BOTTOM) {
      // here we dump the footnote blocks that were referenced in the document body, ie. ones with footnoteOrdinal > 0
      val referencedBlocks = footnoteRepository.getReferencedFootnoteBlocks
      if (referencedBlocks.size() > 0) {
        html.attr("class", "footnotes").withAttr().tagIndent("div", () => {
          html.tagVoidLine("hr")
          html.tagIndent("ol", () => {
            val iter = referencedBlocks.iterator()
            while (iter.hasNext) {
              val footnoteBlock = iter.next()
              val footnoteOrdinal = footnoteBlock.footnoteOrdinal
              html.attr("id", "fn-" + footnoteOrdinal)
              html.withAttr().tagIndent("li", () => {
                context.renderChildren(footnoteBlock)

                val iMax = footnoteBlock.footnoteReferences
                var i = 0
                while (i < iMax) {
                  html.attr("href", "#fnref-" + footnoteOrdinal + (if (i == 0) "" else String.format(Locale.US, "-%d", i: Integer)))
                  if (!footnoteOptions.footnoteBackLinkRefClass.isEmpty) html.attr("class", footnoteOptions.footnoteBackLinkRefClass)
                  html.line()
                  html.withAttr().tag("a")
                  html.raw(footnoteOptions.footnoteBackRefString)
                  html.tag("/a")
                  i += 1
                }
              })
            }
          })
        })
      }
    }
  }

  private def renderFootnoteBlock(node: FootnoteBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    // rendered in document bottom phase
  }

  private def renderFootnote(node: Footnote, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (node.footnoteBlock.isEmpty) {
        //just text
        html.raw("[^")
        context.renderChildren(node)
        html.raw("]")
    } else {
        val footnoteBlock = node.footnoteBlock.get
        val footnoteOrdinal = footnoteBlock.footnoteOrdinal
        val i = node.referenceOrdinal
        html.attr("id", "fnref-" + footnoteOrdinal + (if (i == 0) "" else String.format(Locale.US, "-%d", i: Integer)))
        html.srcPos(node.chars).withAttr().tag("sup", false, false, () => {
          if (!footnoteOptions.footnoteLinkRefClass.isEmpty) html.attr("class", footnoteOptions.footnoteLinkRefClass)
          html.attr("href", "#fn-" + footnoteOrdinal)
          html.withAttr().tag("a")
          html.raw(footnoteOptions.footnoteRefPrefix + footnoteOrdinal + footnoteOptions.footnoteRefSuffix)
          html.tag("/a")
        })
    }
  }
}

object FootnoteNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new FootnoteNodeRenderer(options)
  }
}
