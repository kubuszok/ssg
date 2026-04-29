/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/internal/AsideNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/internal/AsideNodeFormatter.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package aside
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AsideNodeFormatter(options: DataHolder) extends NodeFormatter {

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def getBlockQuoteLikePrefixChar: Char = '|'

  // only registered if assignTextAttributes is enabled
  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[AsideBlock](classOf[AsideBlock], (node, ctx, md) => render(node, ctx, md))
      )
    )

  private def render(node: AsideBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    FormatterUtils.renderBlockQuoteLike(node, context, markdown)
}

object AsideNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new AsideNodeFormatter(options)
  }
}
