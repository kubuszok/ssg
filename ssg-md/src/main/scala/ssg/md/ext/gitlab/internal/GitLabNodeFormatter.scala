/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/internal/GitLabNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gitlab
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class GitLabNodeFormatter(options: DataHolder) extends NodeFormatter {

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[GitLabBlockQuote](classOf[GitLabBlockQuote], (node, ctx, md) => render(node, ctx, md))
      )
    )

  override def getNodeClasses: Nullable[Set[Class[?]]] =
    Nullable(Set[Class[?]](classOf[GitLabBlockQuote]))

  private def render(node: GitLabBlockQuote, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(">>>").line()
    context.renderChildren(node)
    markdown.append(">>>").line()
  }
}

object GitLabNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new GitLabNodeFormatter(options)
  }
}
