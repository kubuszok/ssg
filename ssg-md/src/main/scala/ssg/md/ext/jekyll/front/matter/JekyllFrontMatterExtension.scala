/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/JekyllFrontMatterExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package jekyll
package front
package matter

import ssg.md.ext.jekyll.front.matter.internal.{JekyllFrontMatterBlockParser, JekyllFrontMatterNodeFormatter, JekyllFrontMatterNodeRenderer}
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.MutableDataHolder

/**
 * Extension for jekyll_front_matters.
 *
 * Create it with [[JekyllFrontMatterExtension.create]] and then configure it on the builders.
 *
 * The parsed jekyll_front_matter text is turned into [[JekyllFrontMatterBlock]] nodes.
 */
class JekyllFrontMatterExtension private ()
    extends Parser.ParserExtension,
    HtmlRenderer.HtmlRendererExtension,
    Formatter.FormatterExtension {

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit = {
    formatterBuilder.nodeFormatterFactory(new JekyllFrontMatterNodeFormatter.Factory())
  }

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customBlockParserFactory(new JekyllFrontMatterBlockParser.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new JekyllFrontMatterNodeRenderer.Factory())
    } else if (htmlRendererBuilder.isRendererType("JIRA")) {
      // no-op
    }
  }
}

object JekyllFrontMatterExtension {

  def create(): JekyllFrontMatterExtension = new JekyllFrontMatterExtension()
}
