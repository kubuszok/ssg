/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-superscript/src/main/java/com/vladsch/flexmark/ext/superscript/SuperscriptExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-superscript/src/main/java/com/vladsch/flexmark/ext/superscript/SuperscriptExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package superscript

import ssg.md.ext.superscript.internal.{ SuperscriptDelimiterProcessor, SuperscriptNodeRenderer }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{ MutableDataHolder, NullableDataKey }

/** Extension for superscripts.
  *
  * Create it with [[SuperscriptExtension.create]] and then configure it on the builders.
  *
  * The parsed superscript text is turned into [[Superscript]] nodes.
  */
class SuperscriptExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.customDelimiterProcessor(new SuperscriptDelimiterProcessor())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new SuperscriptNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object SuperscriptExtension {
  val SUPERSCRIPT_STYLE_HTML_OPEN:  NullableDataKey[String] = new NullableDataKey[String]("SUPERSCRIPT_STYLE_HTML_OPEN")
  val SUPERSCRIPT_STYLE_HTML_CLOSE: NullableDataKey[String] = new NullableDataKey[String]("SUPERSCRIPT_STYLE_HTML_CLOSE")

  def create(): SuperscriptExtension = new SuperscriptExtension()
}
