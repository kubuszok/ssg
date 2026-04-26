/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/FootnoteExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/FootnoteExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package footnotes

import ssg.md.ext.footnotes.internal.{ FootnoteBlockParser, FootnoteLinkRefProcessor, FootnoteNodeFormatter, FootnoteNodeRenderer, FootnoteRepository }
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{ DataHolder, DataKey, MutableDataHolder }
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import scala.language.implicitConversions

/** Extension for footnotes
  *
  * Create it with [[FootnoteExtension.create]] and then configure it on the builders
  *
  * The parsed footnote references in text regions are turned into [[Footnote]] nodes. The parsed footnote definitions are turned into [[FootnoteBlock]] nodes.
  */
class FootnoteExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Parser.ReferenceHoldingExtension, Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(new FootnoteNodeFormatter.Factory())

  override def transferReferences(document: MutableDataHolder, included: DataHolder): Boolean =
    if (document.contains(FootnoteExtension.FOOTNOTES) && included.contains(FootnoteExtension.FOOTNOTES)) {
      Parser.transferReferences(
        FootnoteExtension.FOOTNOTES.get(document),
        FootnoteExtension.FOOTNOTES.get(included),
        FootnoteExtension.FOOTNOTES_KEEP.get(document) == KeepType.FIRST
      )
    } else {
      false
    }

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customBlockParserFactory(new FootnoteBlockParser.Factory())
    parserBuilder.linkRefProcessorFactory(new FootnoteLinkRefProcessor.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new FootnoteNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object FootnoteExtension {

  val FOOTNOTES_KEEP: DataKey[KeepType] = new DataKey[KeepType]("FOOTNOTES_KEEP", KeepType.FIRST)
  val FOOTNOTES: DataKey[FootnoteRepository] = new DataKey[FootnoteRepository]("FOOTNOTES", new FootnoteRepository(Nullable.empty), (options: DataHolder) => new FootnoteRepository(Nullable(options)))
  val FOOTNOTE_REF_PREFIX:          DataKey[String] = new DataKey[String]("FOOTNOTE_REF_PREFIX", "")
  val FOOTNOTE_REF_SUFFIX:          DataKey[String] = new DataKey[String]("FOOTNOTE_REF_SUFFIX", "")
  val FOOTNOTE_BACK_REF_STRING:     DataKey[String] = new DataKey[String]("FOOTNOTE_BACK_REF_STRING", "&#8617;")
  val FOOTNOTE_LINK_REF_CLASS:      DataKey[String] = new DataKey[String]("FOOTNOTE_LINK_REF_CLASS", "footnote-ref")
  val FOOTNOTE_BACK_LINK_REF_CLASS: DataKey[String] = new DataKey[String]("FOOTNOTE_BACK_LINK_REF_CLASS", "footnote-backref")

  // formatter options
  val FOOTNOTE_PLACEMENT: DataKey[ElementPlacement]     = new DataKey[ElementPlacement]("FOOTNOTE_PLACEMENT", ElementPlacement.AS_IS)
  val FOOTNOTE_SORT:      DataKey[ElementPlacementSort] = new DataKey[ElementPlacementSort]("FOOTNOTE_SORT", ElementPlacementSort.AS_IS)

  def create(): FootnoteExtension = new FootnoteExtension()
}
