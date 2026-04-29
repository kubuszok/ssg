/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiNodeRenderer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package emoji
package internal

import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class EmojiNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val myOptions: EmojiOptions = new EmojiOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    val set = scala.collection.mutable.HashSet[NodeRenderingHandler[?]]()
    set += (new NodeRenderingHandler[Emoji](classOf[Emoji], (node, ctx, html) => render(node, ctx, html)))
    Nullable(set.toSet)
  }

  private def render(node: Emoji, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val shortcut = EmojiResolvedShortcut.getEmojiText(node, myOptions.useShortcutType, myOptions.useImageType, myOptions.rootImagePath, myOptions.useUnicodeFileNames)

    if (shortcut.emoji.isEmpty || shortcut.emojiText.isEmpty) {
      // output as text
      html.text(":")
      context.renderChildren(node)
      html.text(":")
    } else {
      if (shortcut.isUnicode) {
        html.text(shortcut.emojiText.get)
      } else {
        val resolvedLink = context.resolveLink(LinkType.IMAGE, shortcut.emojiText.get, Nullable.empty)

        html.attr("src", resolvedLink.url)
        html.attr("alt", shortcut.alt.getOrElse(""))
        if (!myOptions.attrImageSize.isEmpty) html.attr("height", myOptions.attrImageSize).attr("width", myOptions.attrImageSize)
        if (!myOptions.attrAlign.isEmpty) html.attr("align", myOptions.attrAlign)
        if (!myOptions.attrImageClass.isEmpty) html.attr("class", myOptions.attrImageClass)
        html.withAttr(resolvedLink)
        html.tagVoid("img")
      }
    }
  }
}

object EmojiNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new EmojiNodeRenderer(options)
  }
}
