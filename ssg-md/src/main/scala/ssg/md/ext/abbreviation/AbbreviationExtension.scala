/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/AbbreviationExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/AbbreviationExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package abbreviation

import ssg.md.ext.abbreviation.internal.{ AbbreviationBlockParser, AbbreviationNodeFormatter, AbbreviationNodePostProcessor, AbbreviationNodeRenderer, AbbreviationRepository }
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{ DataHolder, DataKey, MutableDataHolder }
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import scala.language.implicitConversions

/** Extension for adding abbreviations to markdown
  *
  * Create it with [[AbbreviationExtension.create]] then configure builders
  */
class AbbreviationExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Parser.ReferenceHoldingExtension, Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(new AbbreviationNodeFormatter.Factory())

  override def transferReferences(document: MutableDataHolder, included: DataHolder): Boolean =
    // abbreviations cannot be transferred except before parsing the document
    false

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customBlockParserFactory(new AbbreviationBlockParser.Factory())
    parserBuilder.postProcessorFactory(new AbbreviationNodePostProcessor.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new AbbreviationNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object AbbreviationExtension {

  /** A [[DataKey]] that is used to set the behavior of the abbreviations repository when duplicates are defined. */
  val ABBREVIATIONS_KEEP: DataKey[KeepType] = new DataKey[KeepType]("ABBREVIATIONS_KEEP", KeepType.FIRST)

  /** A [[DataKey]] that is used to get the document's Node repository holding all the abbreviations defined in the current document. */
  val ABBREVIATIONS: DataKey[AbbreviationRepository] = new DataKey[AbbreviationRepository](
    "ABBREVIATIONS",
    new AbbreviationRepository(Nullable.empty),
    (options: DataHolder) => new AbbreviationRepository(Nullable(options))
  )

  /** A [[DataKey]] that is used to set the use links option when true, default is false and abbr tag will be used in the rendered HTML. */
  val USE_LINKS: DataKey[Boolean] = new DataKey[Boolean]("USE_LINKS", false)

  // format options
  val ABBREVIATIONS_PLACEMENT:          DataKey[ElementPlacement]     = new DataKey[ElementPlacement]("ABBREVIATIONS_PLACEMENT", ElementPlacement.AS_IS)
  val ABBREVIATIONS_SORT:               DataKey[ElementPlacementSort] = new DataKey[ElementPlacementSort]("ABBREVIATIONS_SORT", ElementPlacementSort.AS_IS)
  val MAKE_MERGED_ABBREVIATIONS_UNIQUE: DataKey[Boolean]              = new DataKey[Boolean]("MERGE_MAKE_ABBREVIATIONS_UNIQUE", false)

  def create(): AbbreviationExtension = new AbbreviationExtension()
}
