/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/SubscriptExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package strikethrough

import ssg.md.ext.gfm.strikethrough.internal.{StrikethroughNodeRenderer, SubscriptDelimiterProcessor}
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{MutableDataHolder, NullableDataKey}

/**
 * Extension for subscript using ~ (GitHub Flavored Markdown).
 *
 * The parsed subscript text regions are turned into [[Subscript]] nodes.
 */
class SubscriptExtension private () extends Parser.ParserExtension with HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customDelimiterProcessor(new SubscriptDelimiterProcessor())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new StrikethroughNodeRenderer.Factory())
    }
    // Skipping YOUTRACK and JIRA renderers per conversion rules
  }
}

object SubscriptExtension {
  val SUBSCRIPT_STYLE_HTML_OPEN: NullableDataKey[String] = StrikethroughSubscriptExtension.SUBSCRIPT_STYLE_HTML_OPEN
  val SUBSCRIPT_STYLE_HTML_CLOSE: NullableDataKey[String] = StrikethroughSubscriptExtension.SUBSCRIPT_STYLE_HTML_CLOSE

  def create(): SubscriptExtension = new SubscriptExtension()
}
