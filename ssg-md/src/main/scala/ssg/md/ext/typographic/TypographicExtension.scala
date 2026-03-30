/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/TypographicExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package typographic

import ssg.md.ext.typographic.internal.*
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{DataKey, MutableDataHolder, NullableDataKey}
import scala.language.implicitConversions

/**
 * Extension for typographics.
 *
 * Create it with [[TypographicExtension.create]] and then configure it on the builders.
 *
 * The parsed typographic text is turned into [[TypographicQuotes]] and [[TypographicSmarts]] nodes.
 */
class TypographicExtension private () extends Parser.ParserExtension with HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    if (TypographicExtension.ENABLE_QUOTES.get(parserBuilder)) {
      val options = new TypographicOptions(parserBuilder)
      parserBuilder.customDelimiterProcessor(new AngleQuoteDelimiterProcessor(options))
      parserBuilder.customDelimiterProcessor(new SingleQuoteDelimiterProcessor(options))
      parserBuilder.customDelimiterProcessor(new DoubleQuoteDelimiterProcessor(options))
    }
    if (TypographicExtension.ENABLE_SMARTS.get(parserBuilder)) {
      parserBuilder.customInlineParserExtensionFactory(new SmartsInlineParser.Factory())
    }
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML") || htmlRendererBuilder.isRendererType("JIRA")) {
      htmlRendererBuilder.nodeRendererFactory(new TypographicNodeRenderer.Factory())
    }
  }
}

object TypographicExtension {

  val ENABLE_QUOTES: DataKey[Boolean] = new DataKey[Boolean]("ENABLE_QUOTES", true)
  val ENABLE_SMARTS: DataKey[Boolean] = new DataKey[Boolean]("ENABLE_SMARTS", true)
  val ANGLE_QUOTE_CLOSE: DataKey[String] = new DataKey[String]("ANGLE_QUOTE_CLOSE", "&raquo;")
  val ANGLE_QUOTE_OPEN: DataKey[String] = new DataKey[String]("ANGLE_QUOTE_OPEN", "&laquo;")
  val ANGLE_QUOTE_UNMATCHED: NullableDataKey[String] = new NullableDataKey[String]("ANGLE_QUOTE_UNMATCHED")
  val DOUBLE_QUOTE_CLOSE: DataKey[String] = new DataKey[String]("DOUBLE_QUOTE_CLOSE", "&rdquo;")
  val DOUBLE_QUOTE_OPEN: DataKey[String] = new DataKey[String]("DOUBLE_QUOTE_OPEN", "&ldquo;")
  val DOUBLE_QUOTE_UNMATCHED: NullableDataKey[String] = new NullableDataKey[String]("DOUBLE_QUOTE_UNMATCHED")
  val ELLIPSIS: DataKey[String] = new DataKey[String]("ELLIPSIS", "&hellip;")
  val ELLIPSIS_SPACED: DataKey[String] = new DataKey[String]("ELLIPSIS_SPACED", "&hellip;")
  val EM_DASH: DataKey[String] = new DataKey[String]("EM_DASH", "&mdash;")
  val EN_DASH: DataKey[String] = new DataKey[String]("EN_DASH", "&ndash;")
  val SINGLE_QUOTE_CLOSE: DataKey[String] = new DataKey[String]("SINGLE_QUOTE_CLOSE", "&rsquo;")
  val SINGLE_QUOTE_OPEN: DataKey[String] = new DataKey[String]("SINGLE_QUOTE_OPEN", "&lsquo;")
  val SINGLE_QUOTE_UNMATCHED: DataKey[String] = new DataKey[String]("SINGLE_QUOTE_UNMATCHED", "&rsquo;")

  def create(): TypographicExtension = new TypographicExtension()
}
