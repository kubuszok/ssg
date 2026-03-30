/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/NodeRendererSubContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html

import ssg.md.html.renderer.NodeRendererContext
import ssg.md.util.ast.Node

import java.io.IOException

import scala.language.implicitConversions

abstract class NodeRendererSubContext(val htmlWriter: HtmlWriter) extends NodeRendererContext {
  var renderingNode:           Nullable[Node]                        = Nullable.empty
  var renderingHandlerWrapper: Nullable[NodeRenderingHandlerWrapper] = Nullable.empty
  var doNotRenderLinksNesting: Int                                   = 0

  def getHtmlWriter: HtmlWriter = htmlWriter

  def flushTo(out: Appendable, maxTrailingBlankLines: Int): Unit =
    flushTo(out, getHtmlOptions.maxBlankLines, maxTrailingBlankLines)

  def flushTo(out: Appendable, maxBlankLines: Int, maxTrailingBlankLines: Int): Unit = {
    htmlWriter.line()
    try
      htmlWriter.appendTo(out, maxBlankLines, maxTrailingBlankLines)
    catch {
      case e: IOException => e.printStackTrace()
    }
  }

  protected def getDoNotRenderLinksNesting: Int = doNotRenderLinksNesting

  def isDoNotRenderLinks: Boolean = doNotRenderLinksNesting != 0

  def doNotRenderLinks(doNotRenderLinks: Boolean): Unit =
    if (doNotRenderLinks) this.doNotRenderLinks()
    else doRenderLinks()

  def doNotRenderLinks(): Unit =
    doNotRenderLinksNesting += 1

  def doRenderLinks(): Unit = {
    if (doNotRenderLinksNesting == 0) throw new IllegalStateException("Not in do not render links context")
    doNotRenderLinksNesting -= 1
  }
}
