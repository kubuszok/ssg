/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-ins/src/main/java/com/vladsch/flexmark/ext/ins/InsExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package ins

import ssg.md.ext.ins.internal.{InsDelimiterProcessor, InsNodeRenderer}
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{MutableDataHolder, NullableDataKey}

/**
 * Extension for ins.
 *
 * Create it with [[InsExtension.create]] and then configure it on the builders.
 *
 * The parsed ins text is turned into [[Ins]] nodes.
 */
class InsExtension private () extends Parser.ParserExtension with HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customDelimiterProcessor(new InsDelimiterProcessor())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new InsNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object InsExtension {
  val INS_STYLE_HTML_OPEN: NullableDataKey[String] = new NullableDataKey[String]("INS_STYLE_HTML_OPEN")
  val INS_STYLE_HTML_CLOSE: NullableDataKey[String] = new NullableDataKey[String]("INS_STYLE_HTML_CLOSE")

  def create(): InsExtension = new InsExtension()
}
