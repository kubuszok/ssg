/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package toc

import ssg.md.ext.toc.internal.{ SimTocBlockParser, SimTocNodeFormatter, SimTocNodeRenderer, TocOptions }
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.html.renderer.AttributablePart
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataKey, MutableDataHolder, NullableDataKey }

import scala.language.implicitConversions

/** Extension for simulated TOC
  *
  * Create it with [[SimTocExtension.create]] and then configure it on the builders
  */
class SimTocExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {
    if (!options.contains(HtmlRenderer.GENERATE_HEADER_ID)) {
      options.set(HtmlRenderer.GENERATE_HEADER_ID, true)
    }
    if (!options.contains(Formatter.GENERATE_HEADER_ID)) {
      options.set(Formatter.GENERATE_HEADER_ID, true)
    }
    if (!options.contains(HtmlRenderer.RENDER_HEADER_ID)) {
      options.set(HtmlRenderer.RENDER_HEADER_ID, true)
    }
  }

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(new SimTocNodeFormatter.Factory())

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.customBlockParserFactory(new SimTocBlockParser.Factory())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new SimTocNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object SimTocExtension {

  // duplicated here for convenience
  val TOC_CONTENT: AttributablePart = TocUtils.TOC_CONTENT
  val TOC_LIST:    AttributablePart = TocUtils.TOC_LIST

  val LEVELS:                 DataKey[Integer]             = TocExtension.LEVELS
  val IS_TEXT_ONLY:           DataKey[Boolean]             = TocExtension.IS_TEXT_ONLY
  val IS_NUMBERED:            DataKey[Boolean]             = TocExtension.IS_NUMBERED
  val LIST_TYPE:              DataKey[TocOptions.ListType] = TocExtension.LIST_TYPE
  val IS_HTML:                DataKey[Boolean]             = TocExtension.IS_HTML
  val TITLE_LEVEL:            DataKey[Integer]             = TocExtension.TITLE_LEVEL
  val TITLE:                  NullableDataKey[String]      = TocExtension.TITLE
  val AST_INCLUDE_OPTIONS:    DataKey[Boolean]             = TocExtension.AST_INCLUDE_OPTIONS
  val BLANK_LINE_SPACER:      DataKey[Boolean]             = TocExtension.BLANK_LINE_SPACER
  val DIV_CLASS:              DataKey[String]              = TocExtension.DIV_CLASS
  val LIST_CLASS:             DataKey[String]              = TocExtension.LIST_CLASS
  val CASE_SENSITIVE_TOC_TAG: DataKey[Boolean]             = TocExtension.CASE_SENSITIVE_TOC_TAG

  // format options
  val FORMAT_UPDATE_ON_FORMAT: DataKey[SimTocGenerateOnFormat] = TocExtension.FORMAT_UPDATE_ON_FORMAT
  val FORMAT_OPTIONS:          DataKey[TocOptions]             = TocExtension.FORMAT_OPTIONS

  def create(): SimTocExtension = new SimTocExtension()
}
