/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/SimTocNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

// TODO: Full SimTocNodeFormatter port pending
class SimTocNodeFormatter(options: DataHolder) extends NodeFormatter {

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] = {
    Nullable(Set[NodeFormattingHandler[?]](
      new NodeFormattingHandler[SimTocBlock](classOf[SimTocBlock], (node, ctx, md) => render(node, ctx, md))
    ))
  }

  private def render(node: SimTocBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    // TODO: implement SimToc formatting
    context.renderChildren(node)
  }
}

object SimTocNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new SimTocNodeFormatter(options)
  }
}
