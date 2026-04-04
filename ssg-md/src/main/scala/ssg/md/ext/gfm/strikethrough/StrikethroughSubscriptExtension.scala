/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/StrikethroughSubscriptExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package strikethrough

import ssg.md.ext.gfm.strikethrough.internal.{ StrikethroughNodeRenderer, StrikethroughSubscriptDelimiterProcessor }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{ MutableDataHolder, NullableDataKey }

/** Extension for GFM strikethrough using ~~ (GitHub Flavored Markdown).
  *
  * The parsed strikethrough text regions are turned into [[Strikethrough]] nodes.
  */
class StrikethroughSubscriptExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.customDelimiterProcessor(new StrikethroughSubscriptDelimiterProcessor())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new StrikethroughNodeRenderer.Factory())
    }
    // Skipping YOUTRACK and JIRA renderers per conversion rules
  }
}

object StrikethroughSubscriptExtension {
  val STRIKETHROUGH_STYLE_HTML_OPEN:  NullableDataKey[String] = new NullableDataKey[String]("STRIKETHROUGH_STYLE_HTML_OPEN")
  val STRIKETHROUGH_STYLE_HTML_CLOSE: NullableDataKey[String] = new NullableDataKey[String]("STRIKETHROUGH_STYLE_HTML_CLOSE")
  val SUBSCRIPT_STYLE_HTML_OPEN:      NullableDataKey[String] = new NullableDataKey[String]("SUBSCRIPT_STYLE_HTML_OPEN")
  val SUBSCRIPT_STYLE_HTML_CLOSE:     NullableDataKey[String] = new NullableDataKey[String]("SUBSCRIPT_STYLE_HTML_CLOSE")

  def create(): StrikethroughSubscriptExtension = new StrikethroughSubscriptExtension()
}
