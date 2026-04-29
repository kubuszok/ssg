/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/StrikethroughExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/StrikethroughExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gfm
package strikethrough

import ssg.md.ext.gfm.strikethrough.internal.{ StrikethroughDelimiterProcessor, StrikethroughNodeRenderer }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{ MutableDataHolder, NullableDataKey }

/** Extension for GFM strikethrough using ~~ (GitHub Flavored Markdown).
  *
  * The parsed strikethrough text regions are turned into [[Strikethrough]] nodes.
  */
class StrikethroughExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.customDelimiterProcessor(new StrikethroughDelimiterProcessor())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new StrikethroughNodeRenderer.Factory())
    }
    // Skipping YOUTRACK and JIRA renderers per conversion rules
  }
}

object StrikethroughExtension {
  val STRIKETHROUGH_STYLE_HTML_OPEN:  NullableDataKey[String] = StrikethroughSubscriptExtension.STRIKETHROUGH_STYLE_HTML_OPEN
  val STRIKETHROUGH_STYLE_HTML_CLOSE: NullableDataKey[String] = StrikethroughSubscriptExtension.STRIKETHROUGH_STYLE_HTML_CLOSE

  def create(): StrikethroughExtension = new StrikethroughExtension()
}
