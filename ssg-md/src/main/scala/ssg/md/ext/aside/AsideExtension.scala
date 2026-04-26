/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/AsideExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/AsideExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package aside

import ssg.md.ext.aside.internal.{ AsideBlockParser, AsideNodeFormatter, AsideNodeRenderer }
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataKey, MutableDataHolder }

/** Extension for ext_asides.
  *
  * Create it with [[AsideExtension.create]] and then configure it on the builders.
  *
  * The parsed pipe prefixed text is turned into [[AsideBlock]] nodes.
  */
class AsideExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(new AsideNodeFormatter.Factory())

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.customBlockParserFactory(new AsideBlockParser.Factory())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new AsideNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object AsideExtension {
  val EXTEND_TO_BLANK_LINE:                       DataKey[Boolean] = new DataKey[Boolean]("EXTEND_TO_BLANK_LINE", Parser.BLOCK_QUOTE_EXTEND_TO_BLANK_LINE)
  val IGNORE_BLANK_LINE:                          DataKey[Boolean] = new DataKey[Boolean]("IGNORE_BLANK_LINE", Parser.BLOCK_QUOTE_IGNORE_BLANK_LINE)
  val ALLOW_LEADING_SPACE:                        DataKey[Boolean] = new DataKey[Boolean]("ALLOW_LEADING_SPACE", Parser.BLOCK_QUOTE_ALLOW_LEADING_SPACE)
  val INTERRUPTS_PARAGRAPH:                       DataKey[Boolean] = new DataKey[Boolean]("INTERRUPTS_PARAGRAPH", Parser.BLOCK_QUOTE_INTERRUPTS_PARAGRAPH)
  val INTERRUPTS_ITEM_PARAGRAPH:                  DataKey[Boolean] = new DataKey[Boolean]("INTERRUPTS_ITEM_PARAGRAPH", Parser.BLOCK_QUOTE_INTERRUPTS_ITEM_PARAGRAPH)
  val WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH: DataKey[Boolean] = new DataKey[Boolean]("WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH", Parser.BLOCK_QUOTE_WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH)

  def create(): AsideExtension = new AsideExtension()
}
