/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-issues/src/main/java/com/vladsch/flexmark/ext/gfm/issues/internal/GfmIssuesNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package issues
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler}
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class GfmIssuesNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val gfmIssuesOptions = new GfmIssuesOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set(
      new NodeRenderingHandler[GfmIssue](classOf[GfmIssue], (node, ctx, html) => render(node, ctx, html))
    ))
  }

  private def render(node: GfmIssue, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (context.isDoNotRenderLinks) {
      html.text(node.chars)
    } else {
      val sb = new StringBuilder()

      sb.append(gfmIssuesOptions.gitHubIssuesUrlRoot).append(gfmIssuesOptions.gitHubIssueUrlPrefix).append(node.text).append(gfmIssuesOptions.gitHubIssueUrlSuffix)

      html.srcPos(node.chars).attr("href", sb.toString()).withAttr().tag("a")
      html.raw(gfmIssuesOptions.gitHubIssueTextPrefix)
      html.text(node.chars)
      html.raw(gfmIssuesOptions.gitHubIssueTextSuffix)
      html.tag("/a")
    }
  }
}

object GfmIssuesNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new GfmIssuesNodeRenderer(options)
  }
}
