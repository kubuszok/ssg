/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/internal/JekyllFrontMatterNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package jekyll
package front
package matter
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions
import ssg.md.util.ast.Document

class JekyllFrontMatterNodeFormatter(options: DataHolder) extends PhasedNodeFormatter {

  override def getFormattingPhases: Nullable[Set[FormattingPhase]] =
    Nullable(Set[FormattingPhase](FormattingPhase.DOCUMENT_FIRST))

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def renderDocument(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document, phase: FormattingPhase): Unit =
    if (phase == FormattingPhase.DOCUMENT_FIRST) {
      document.firstChild.foreach { node =>
        if (node.isInstanceOf[JekyllFrontMatterBlock]) {
          markdown.openPreFormatted(false)
          markdown.append(node.chars).blankLine()
          markdown.closePreFormatted()
        }
      }
    }

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[JekyllFrontMatterBlock](classOf[JekyllFrontMatterBlock], (node, context, markdown) => render(node, context, markdown))
      )
    )

  private def render(node: JekyllFrontMatterBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {}
}

object JekyllFrontMatterNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new JekyllFrontMatterNodeFormatter(options)
  }
}
