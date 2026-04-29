/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-escaped-character/src/main/java/com/vladsch/flexmark/ext/escaped/character/internal/EscapedCharacterNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-escaped-character/src/main/java/com/vladsch/flexmark/ext/escaped/character/internal/EscapedCharacterNodeRenderer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package escaped
package character
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{ NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class EscapedCharacterNodeRenderer(options: DataHolder) extends NodeRenderer {

  @SuppressWarnings(Array("unused")) @annotation.nowarn("msg=unused private member")
  private val escapedCharacterOptions = new EscapedCharacterOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set(
        new NodeRenderingHandler[EscapedCharacter](classOf[EscapedCharacter], (node, ctx, html) => render(node, ctx, html))
      )
    )

  private def render(node: EscapedCharacter, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.text(node.chars.unescape())
}

object EscapedCharacterNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new EscapedCharacterNodeRenderer(options)
  }
}
