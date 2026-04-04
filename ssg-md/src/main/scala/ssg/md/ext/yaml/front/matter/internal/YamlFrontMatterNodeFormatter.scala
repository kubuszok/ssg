/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/internal/YamlFrontMatterNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package yaml
package front
package matter
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions
import ssg.md.util.ast.Document

class YamlFrontMatterNodeFormatter(options: DataHolder) extends PhasedNodeFormatter {

  override def getFormattingPhases: Nullable[Set[FormattingPhase]] =
    Nullable(Set[FormattingPhase](FormattingPhase.DOCUMENT_FIRST))

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def renderDocument(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document, phase: FormattingPhase): Unit =
    if (phase == FormattingPhase.DOCUMENT_FIRST) {
      document.firstChild.foreach { node =>
        if (node.isInstanceOf[YamlFrontMatterBlock]) {
          markdown.openPreFormatted(false)
          markdown.append(node.chars).blankLine()
          markdown.closePreFormatted()
        }
      }
    }

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[YamlFrontMatterBlock](classOf[YamlFrontMatterBlock], (node, context, markdown) => render(node, context, markdown))
      )
    )

  private def render(node: YamlFrontMatterBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {}
}

object YamlFrontMatterNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new YamlFrontMatterNodeFormatter(options)
  }
}
