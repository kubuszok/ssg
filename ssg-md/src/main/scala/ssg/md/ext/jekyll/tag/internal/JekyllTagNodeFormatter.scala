/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagNodeFormatter.java
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
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions
import ssg.md.util.ast.Document

class JekyllTagNodeFormatter(options: DataHolder) extends PhasedNodeFormatter {

  private var embedIncludedContent: Boolean = false

  override def getFormattingPhases: Nullable[Set[FormattingPhase]] =
    Nullable(Set(FormattingPhase.COLLECT))

  override def renderDocument(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document, phase: FormattingPhase): Unit =
    this.embedIncludedContent = JekyllTagExtension.EMBED_INCLUDED_CONTENT.get(document)

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[JekyllTagBlock](classOf[JekyllTagBlock], (node, context, markdown) => renderBlock(node, context, markdown)),
        new NodeFormattingHandler[JekyllTag](classOf[JekyllTag], (node, context, markdown) => renderTag(node, context, markdown))
      )
    )

  private def renderBlock(node: JekyllTagBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    context.renderChildren(node)

  private def renderTag(node: JekyllTag, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (embedIncludedContent) {
      // remove jekyll tag node and just leave the included content
      context.renderChildren(node)
    } else {
      if (node.parent.isEmpty || !node.parent.get.isInstanceOf[JekyllTagBlock]) {
        val prev = node.previous
        if (prev.isDefined) {
          val prevChars = prev.get.chars
          markdown.pushOptions().preserveSpaces().append(prevChars.baseSubSequence(prevChars.endOffset, node.startOffset)).popOptions()
        } else {
          val startLine = node.baseSequence.startOfLine(node.startOffset)
          if (startLine < node.startOffset) {
            val nodeChars = node.baseSubSequence(startLine, node.startOffset)
            markdown.pushOptions().preserveSpaces().append(nodeChars).popOptions()
          }
        }
      }

      markdown.append(node.chars)
    }
}

object JekyllTagNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new JekyllTagNodeFormatter(options)
  }
}
