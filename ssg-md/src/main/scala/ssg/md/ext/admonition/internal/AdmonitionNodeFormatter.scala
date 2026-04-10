/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/internal/AdmonitionNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package admonition
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.RepeatedSequence

import scala.language.implicitConversions

class AdmonitionNodeFormatter(options: DataHolder) extends NodeFormatter {

  private val admonitionOptions: AdmonitionOptions = new AdmonitionOptions(options)

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[AdmonitionBlock](classOf[AdmonitionBlock], (node, ctx, md) => render(node, ctx, md))
      )
    )

  private def render(node: AdmonitionBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine()
    markdown.append(node.openingMarker).append(' ')
    markdown.appendNonTranslating(node.info)
    if (node.title.isNotNull) {
      markdown.append(' ').append('"')
      markdown.appendTranslating(node.title)
      markdown.append('"')
    }
    markdown.line()
    markdown.pushPrefix().addPrefix(RepeatedSequence.repeatOf(" ", admonitionOptions.contentIndent).toString)
    context.renderChildren(node)
    markdown.blankLine()
    markdown.popPrefix()
  }
}

object AdmonitionNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new AdmonitionNodeFormatter(options)
  }
}
