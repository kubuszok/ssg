/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/NodeFormatterSubContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.util.ast.Node

import java.io.IOException
import scala.language.implicitConversions

abstract class NodeFormatterSubContext(protected val markdown: MarkdownWriter) extends NodeFormatterContext {

  var renderingNode: Nullable[Node]                           = Nullable.empty
  var rendererList:  Nullable[List[NodeFormattingHandler[?]]] = Nullable.empty
  var rendererIndex: Int                                      = -1

  override def getMarkdown: MarkdownWriter = markdown

  def flushTo(out: Appendable, maxTrailingBlankLines: Int): Unit =
    flushTo(out, getFormatterOptions.maxBlankLines, maxTrailingBlankLines)

  def flushTo(out: Appendable, maxBlankLines: Int, maxTrailingBlankLines: Int): Unit = {
    markdown.line()
    try
      markdown.appendTo(out, maxBlankLines, maxTrailingBlankLines)
    catch {
      case e: IOException => e.printStackTrace()
    }
  }
}
