/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/internal/GfmUsersNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package users
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler}
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class GfmUsersNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val gfmUsersOptions = new GfmUsersOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    Nullable(Set(
      new NodeRenderingHandler[GfmUser](classOf[GfmUser], (node, ctx, html) => render(node, ctx, html))
    ))
  }

  private def render(node: GfmUser, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (context.isDoNotRenderLinks) {
      html.text(node.chars)
    } else {
      val sb = new StringBuilder()

      sb.append(gfmUsersOptions.gitHubIssuesUrlRoot).append(gfmUsersOptions.gitHubIssueUrlPrefix).append(node.text).append(gfmUsersOptions.gitHubIssueUrlSuffix)

      html.srcPos(node.chars).attr("href", sb.toString()).withAttr().tag("a")
      html.raw(gfmUsersOptions.gitHubUserTextPrefix)
      html.text(node.chars)
      html.raw(gfmUsersOptions.gitHubUserTextSuffix)
      html.tag("/a")
    }
  }
}

object GfmUsersNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new GfmUsersNodeRenderer(options)
  }
}
