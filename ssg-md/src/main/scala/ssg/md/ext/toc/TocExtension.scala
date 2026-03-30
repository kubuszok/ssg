/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/TocExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc

import ssg.md.ext.toc.internal.{TocBlockParser, TocNodeRenderer, TocOptions}
import ssg.md.html.HtmlRenderer
import ssg.md.html.renderer.AttributablePart
import ssg.md.parser.Parser
import ssg.md.util.data.{DataKey, MutableDataHolder, NullableDataKey}

import scala.language.implicitConversions

/**
 * Extension for TOC
 *
 * Create it with [[TocExtension.create]] and then configure it on the builders
 *
 * The parsed [TOC] text is turned into [[TocBlock]] nodes.
 * Rendered into table of contents based on the headings in the document.
 */
class TocExtension private () extends Parser.ParserExtension with HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {
    // set header id options if not already set
    if (!options.contains(HtmlRenderer.RENDER_HEADER_ID)) {
      options.set(HtmlRenderer.RENDER_HEADER_ID, true)
      options.set(HtmlRenderer.GENERATE_HEADER_ID, true)
    } else if (!options.contains(HtmlRenderer.GENERATE_HEADER_ID)) {
      options.set(HtmlRenderer.GENERATE_HEADER_ID, true)
    }
  }

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customBlockParserFactory(new TocBlockParser.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new TocNodeRenderer.Factory())
    }
  }
}

object TocExtension {

  // duplicated here for convenience
  val TOC_CONTENT: AttributablePart = TocUtils.TOC_CONTENT
  val TOC_LIST: AttributablePart = TocUtils.TOC_LIST

  val LEVELS: DataKey[Integer] = new DataKey[Integer]("LEVELS", TocOptions.DEFAULT_LEVELS)
  val IS_TEXT_ONLY: DataKey[Boolean] = new DataKey[Boolean]("IS_TEXT_ONLY", false)
  val IS_NUMBERED: DataKey[Boolean] = new DataKey[Boolean]("IS_NUMBERED", false)
  val LIST_TYPE: DataKey[TocOptions.ListType] = new DataKey[TocOptions.ListType]("LIST_TYPE", TocOptions.ListType.HIERARCHY)
  val IS_HTML: DataKey[Boolean] = new DataKey[Boolean]("IS_HTML", false)
  val TITLE_LEVEL: DataKey[Integer] = new DataKey[Integer]("TITLE_LEVEL", TocOptions.DEFAULT_TITLE_LEVEL)
  val TITLE: NullableDataKey[String] = new NullableDataKey[String]("TITLE")
  val AST_INCLUDE_OPTIONS: DataKey[Boolean] = new DataKey[Boolean]("AST_INCLUDE_OPTIONS", false)
  val BLANK_LINE_SPACER: DataKey[Boolean] = new DataKey[Boolean]("BLANK_LINE_SPACER", false)
  val DIV_CLASS: DataKey[String] = new DataKey[String]("DIV_CLASS", "")
  val LIST_CLASS: DataKey[String] = new DataKey[String]("LIST_CLASS", "")
  val CASE_SENSITIVE_TOC_TAG: DataKey[Boolean] = new DataKey[Boolean]("CASE_SENSITIVE_TOC_TAG", true)

  // format options
  val FORMAT_UPDATE_ON_FORMAT: DataKey[SimTocGenerateOnFormat] = new DataKey[SimTocGenerateOnFormat]("FORMAT_UPDATE_ON_FORMAT", SimTocGenerateOnFormat.UPDATE)
  @annotation.nowarn("msg=null")
  val FORMAT_OPTIONS: DataKey[TocOptions] = new DataKey[TocOptions]("FORMAT_OPTIONS", TocOptions.fromOptions(null, false), (options: ssg.md.util.data.DataHolder) => TocOptions.fromOptions(options, false)) // @nowarn - Java interop: null default

  def create(): TocExtension = new TocExtension()
}
