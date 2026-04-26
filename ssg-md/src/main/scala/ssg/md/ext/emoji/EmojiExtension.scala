/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/EmojiExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/EmojiExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package emoji

import ssg.md.ext.emoji.internal.{ EmojiDelimiterProcessor, EmojiNodeFormatter, EmojiNodeRenderer }
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataKey, MutableDataHolder }

/** Extension for emoji shortcuts using Emoji-Cheat-Sheet.com.
  *
  * Create it with [[EmojiExtension.create]] and then configure it on the builders.
  *
  * The parsed emoji shortcuts text regions are turned into [[Emoji]] nodes.
  */
class EmojiExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(new EmojiNodeFormatter.Factory())

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.customDelimiterProcessor(new EmojiDelimiterProcessor())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new EmojiNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object EmojiExtension {

  val ATTR_ALIGN:             DataKey[String]            = new DataKey[String]("ATTR_ALIGN", "absmiddle")
  val ATTR_IMAGE_SIZE:        DataKey[String]            = new DataKey[String]("ATTR_IMAGE_SIZE", "20")
  val ATTR_IMAGE_CLASS:       DataKey[String]            = new DataKey[String]("ATTR_IMAGE_CLASS", "")
  val ROOT_IMAGE_PATH:        DataKey[String]            = new DataKey[String]("ROOT_IMAGE_PATH", "/img/")
  val USE_SHORTCUT_TYPE:      DataKey[EmojiShortcutType] = new DataKey[EmojiShortcutType]("USE_SHORTCUT_TYPE", EmojiShortcutType.EMOJI_CHEAT_SHEET)
  val USE_IMAGE_TYPE:         DataKey[EmojiImageType]    = new DataKey[EmojiImageType]("USE_IMAGE_TYPE", EmojiImageType.IMAGE_ONLY)
  val USE_UNICODE_FILE_NAMES: DataKey[Boolean]           = new DataKey[Boolean]("USE_UNICODE_FILE_NAMES", false)

  def create(): EmojiExtension = new EmojiExtension()
}
