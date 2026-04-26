/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: com.vladsch.flexmark.ext.enumerated.reference.internal → ssg.md.ext.enumerated.reference.internal
 *   Convention: Scala 3, Nullable[A], no return
 *   Idiom: match instead of switch, boundary/break for early exit
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceNodeFormatter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.Nullable
import ssg.md.ext.attributes.internal.AttributesNodeFormatter
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class EnumeratedReferenceNodeFormatter(options: DataHolder)
    extends NodeRepositoryFormatter[EnumeratedReferenceRepository, EnumeratedReferenceBlock, EnumeratedReferenceText](
      options,
      null, // @nowarn - no attribute translation map needed for enumerated references
      AttributesNodeFormatter.ATTRIBUTE_UNIQUIFICATION_CATEGORY_MAP
    ) {

  private val formatOptions = new EnumeratedReferenceFormatOptions(options)

  override def getRepository(options: DataHolder): EnumeratedReferenceRepository =
    EnumeratedReferenceExtension.ENUMERATED_REFERENCES.get(options)

  override def getReferencePlacement: ElementPlacement = formatOptions.enumeratedReferencePlacement

  override def getReferenceSort: ElementPlacementSort = formatOptions.enumeratedReferenceSort

  override def renderReferenceBlock(node: EnumeratedReferenceBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine()
    markdown.append("[@")
    markdown.appendNonTranslating(node.text)
    markdown.append("]: ")
    markdown.pushPrefix().addPrefix("    ", true)
    context.renderChildren(node)
    markdown.popPrefix()
    markdown.blankLine()
  }

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[EnumeratedReferenceText](classOf[EnumeratedReferenceText], (node, ctx, md) => renderText(node, ctx, md)),
        new NodeFormattingHandler[EnumeratedReferenceLink](classOf[EnumeratedReferenceLink], (node, ctx, md) => renderLink(node, ctx, md)),
        new NodeFormattingHandler[EnumeratedReferenceBlock](classOf[EnumeratedReferenceBlock], (node, ctx, md) => renderBlock(node, ctx, md))
      )
    )

  override def getNodeClasses: Nullable[Set[Class[?]]] =
    if (formatOptions.enumeratedReferencePlacement.isNoChange || !formatOptions.enumeratedReferenceSort.isUnused) Nullable.empty
    else {
      Nullable(Set[Class[?]](classOf[EnumeratedReferenceBlock]))
    }

  private def renderBlock(node: EnumeratedReferenceBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderReference(node, context, markdown)

  private def renderText(node: EnumeratedReferenceText, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append("[#")
    if (context.isTransformingText) {
      EnumeratedReferenceNodeFormatter.renderReferenceText(node.text, context, markdown)
    } else {
      context.renderChildren(node)
    }
    markdown.append("]")
  }

  private def renderLink(node: EnumeratedReferenceLink, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append("[@")
    if (context.isTransformingText) {
      EnumeratedReferenceNodeFormatter.renderReferenceText(node.text, context, markdown)
    } else {
      context.renderChildren(node)
    }
    markdown.append("]")
  }
}

object EnumeratedReferenceNodeFormatter {

  private def renderReferenceText(text: BasedSequence, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (!text.isEmpty) {
      val valueChars = text
      val pos        = valueChars.indexOf(':')
      var category: String = null // @nowarn - Java interop: null used for optional category id split
      var id:       String = null // @nowarn
      if (pos == -1) {
        category = text.toString
      } else {
        category = valueChars.subSequence(0, pos).toString
        id = valueChars.subSequence(pos + 1).toString
      }

      val encoded = AttributesNodeFormatter.getEncodedIdAttribute(category, id, context, markdown)
      markdown.append(encoded)
    }

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new EnumeratedReferenceNodeFormatter(options)

    override def afterDependents: Nullable[Set[Class[?]]] =
      // run before attributes formatter so categories are uniquified first
      // renderers are sorted in reverse order for backward compatibility
      Nullable(Set[Class[?]](classOf[AttributesNodeFormatter.Factory]))

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty
  }
}
