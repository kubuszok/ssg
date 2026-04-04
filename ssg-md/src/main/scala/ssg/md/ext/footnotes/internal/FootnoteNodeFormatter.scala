/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/internal/FootnoteNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.{ DataHolder, DataKey }
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import java.{ util => ju }
import scala.language.implicitConversions

class FootnoteNodeFormatter(options: DataHolder)
    extends NodeRepositoryFormatter[FootnoteRepository, FootnoteBlock, Footnote](
      options,
      FootnoteNodeFormatter.FOOTNOTE_TRANSLATION_MAP,
      FootnoteNodeFormatter.FOOTNOTE_UNIQUIFICATION_MAP
    ) {

  private val formatOptions: FootnoteFormatOptions = new FootnoteFormatOptions(options)

  override def getRepository(options: DataHolder): FootnoteRepository = FootnoteExtension.FOOTNOTES.get(options)

  override def getReferencePlacement: ElementPlacement = formatOptions.footnotePlacement

  override def getReferenceSort: ElementPlacementSort = formatOptions.footnoteSort

  override def renderReferenceBlock(node: FootnoteBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine().append("[^")
    markdown.append(transformReferenceId(node.text.toString, context))
    markdown.append("]: ")
    markdown.pushPrefix().addPrefix("    ")
    context.renderChildren(node)
    markdown.popPrefix()
    markdown.blankLine()
  }

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[Footnote](classOf[Footnote], (node, ctx, md) => renderFootnote(node, ctx, md)),
        new NodeFormattingHandler[FootnoteBlock](classOf[FootnoteBlock], (node, ctx, md) => renderFootnoteBlock(node, ctx, md))
      )
    )

  override def getNodeClasses: Nullable[Set[Class[?]]] =
    if (formatOptions.footnotePlacement.isNoChange || !formatOptions.footnoteSort.isUnused) Nullable.empty
    else Nullable(Set[Class[?]](classOf[Footnote]))

  private def renderFootnoteBlock(node: FootnoteBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderReference(node, context, markdown)

  private def renderFootnote(node: Footnote, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append("[^")
    if (context.isTransformingText) {
      val referenceId = transformReferenceId(node.text.toString, context)
      context.nonTranslatingSpan((context1, markdown1) => markdown1.append(referenceId))
    } else {
      markdown.append(node.text)
    }
    markdown.append("]")
  }
}

object FootnoteNodeFormatter {

  val FOOTNOTE_TRANSLATION_MAP:    DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]]("FOOTNOTE_TRANSLATION_MAP", new ju.HashMap[String, String]())
  val FOOTNOTE_UNIQUIFICATION_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]]("FOOTNOTE_UNIQUIFICATION_MAP", new ju.HashMap[String, String]())

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new FootnoteNodeFormatter(options)
  }
}
