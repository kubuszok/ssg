/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder
import ssg.md.util.format.options.{ElementPlacement, ElementPlacementSort}
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class EnumeratedReferenceNodeFormatter(options: DataHolder) extends NodeRepositoryFormatter[EnumeratedReferenceRepository, EnumeratedReferenceBlock, EnumeratedReferenceText](
  options, null, null // @nowarn - translation/uniquification maps not needed for enumerated references
) {

  private val formatOptions = new EnumeratedReferenceFormatOptions(options)

  override def getRepository(options: DataHolder): EnumeratedReferenceRepository = {
    EnumeratedReferenceExtension.ENUMERATED_REFERENCES.get(options)
  }

  override def getReferencePlacement: ElementPlacement = formatOptions.enumeratedReferencePlacement

  override def getReferenceSort: ElementPlacementSort = formatOptions.enumeratedReferenceSort

  override def renderReferenceBlock(node: EnumeratedReferenceBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    // TODO: appendNonTranslating not yet ported - using append for now
    markdown.blankLine().append("[@").append(node.text).append("]: ")
    markdown.pushPrefix().addPrefix("    ", true)
    context.renderChildren(node)
    markdown.popPrefix()
    markdown.blankLine()
  }

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] = {
    Nullable(Set[NodeFormattingHandler[?]](
      new NodeFormattingHandler[EnumeratedReferenceText](classOf[EnumeratedReferenceText], (node, ctx, md) => renderText(node, ctx, md)),
      new NodeFormattingHandler[EnumeratedReferenceLink](classOf[EnumeratedReferenceLink], (node, ctx, md) => renderLink(node, ctx, md)),
      new NodeFormattingHandler[EnumeratedReferenceBlock](classOf[EnumeratedReferenceBlock], (node, ctx, md) => renderBlock(node, ctx, md))
    ))
  }

  override def getNodeClasses: Nullable[Set[Class[?]]] = {
    if (formatOptions.enumeratedReferencePlacement.isNoChange || !formatOptions.enumeratedReferenceSort.isUnused) Nullable.empty
    else {
      Nullable(Set[Class[?]](classOf[EnumeratedReferenceBlock]))
    }
  }

  private def renderBlock(node: EnumeratedReferenceBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    renderReference(node, context, markdown)
  }

  private def renderText(node: EnumeratedReferenceText, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append("[#")
    if (context.isTransformingText) {
      renderReferenceText(node.text, context, markdown)
    } else {
      context.renderChildren(node)
    }
    markdown.append("]")
  }

  private def renderLink(node: EnumeratedReferenceLink, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append("[@")
    if (context.isTransformingText) {
      renderReferenceText(node.text, context, markdown)
    } else {
      context.renderChildren(node)
    }
    markdown.append("]")
  }

  private def renderReferenceText(text: BasedSequence, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (!text.isEmpty) {
      val valueChars = text
      val pos = valueChars.indexOf(':')
      @annotation.nowarn("msg=unused local definition") // will be used when AttributesNodeFormatter.getEncodedIdAttribute is ported
      val category: String = if (pos == -1) text.toString else valueChars.subSequence(0, pos).toString
      // NOTE: AttributesNodeFormatter.getEncodedIdAttribute not yet fully ported,
      // falling back to direct category:id output
      markdown.append(text)
    }
  }
}

object EnumeratedReferenceNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new EnumeratedReferenceNodeFormatter(options)
  }
}
