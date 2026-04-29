/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package definition

import ssg.md.ext.definition.internal.*
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataKey, MutableDataHolder }
import ssg.md.util.format.options.DefinitionMarker

import scala.language.implicitConversions

/** Extension for definitions
  *
  * Create it with [[DefinitionExtension.create]] and then configure it on the builders
  *
  * The parsed definition text is turned into [[DefinitionList]], [[DefinitionTerm]] and [[DefinitionItem]] nodes.
  */
class DefinitionExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(new DefinitionNodeFormatter.Factory())

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customBlockParserFactory(new DefinitionItemBlockParser.Factory())
    parserBuilder.blockPreProcessorFactory(new DefinitionListItemBlockPreProcessor.Factory())
    parserBuilder.blockPreProcessorFactory(new DefinitionListBlockPreProcessor.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new DefinitionNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object DefinitionExtension {

  val COLON_MARKER:                  DataKey[Boolean] = new DataKey[Boolean]("COLON_MARKER", true)
  val MARKER_SPACES:                 DataKey[Integer] = new DataKey[Integer]("MARKER_SPACE", 1)
  val TILDE_MARKER:                  DataKey[Boolean] = new DataKey[Boolean]("TILDE_MARKER", true)
  val DOUBLE_BLANK_LINE_BREAKS_LIST: DataKey[Boolean] = new DataKey[Boolean]("DOUBLE_BLANK_LINE_BREAKS_LIST", false)

  val FORMAT_MARKER_SPACES: DataKey[Integer]          = new DataKey[Integer]("MARKER_SPACE", 3)
  val FORMAT_MARKER_TYPE:   DataKey[DefinitionMarker] = new DataKey[DefinitionMarker]("FORMAT_MARKER_TYPE", DefinitionMarker.ANY)

  def create(): DefinitionExtension = new DefinitionExtension()
}
