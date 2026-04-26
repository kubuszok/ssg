/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/MacrosExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/MacrosExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package macros

import ssg.md.ext.macros.internal.*
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{ DataHolder, DataKey, MutableDataHolder }
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

/** Extension for macros.
  *
  * Create it with [[MacrosExtension.create]] and then configure it on the builders.
  *
  * The parsed macros text is turned into [[MacroReference]] nodes.
  */
import scala.language.implicitConversions

class MacrosExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Parser.ReferenceHoldingExtension, Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def transferReferences(document: MutableDataHolder, included: DataHolder): Boolean =
    // cannot optimize based on macros in this document, repository is not accessed until rendering
    if (included.contains(MacrosExtension.MACRO_DEFINITIONS)) {
      Parser.transferReferences(
        MacrosExtension.MACRO_DEFINITIONS.get(document),
        MacrosExtension.MACRO_DEFINITIONS.get(included),
        MacrosExtension.MACRO_DEFINITIONS_KEEP.get(document) == KeepType.FIRST
      )
    } else {
      false
    }

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(new MacrosNodeFormatter.Factory())

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customBlockParserFactory(new MacroDefinitionBlockParser.Factory())
    parserBuilder.customInlineParserExtensionFactory(new MacrosInlineParserExtension.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new MacrosNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object MacrosExtension {
  val MACRO_DEFINITIONS_KEEP: DataKey[KeepType]                  = new DataKey[KeepType]("MACRO_DEFINITIONS_KEEP", KeepType.FIRST) // standard option to allow control over how to handle duplicates
  val MACRO_DEFINITIONS:      DataKey[MacroDefinitionRepository] =
    new DataKey[MacroDefinitionRepository](
      "MACRO_DEFINITIONS",
      new MacroDefinitionRepository(new ssg.md.util.data.MutableDataSet()),
      (options: DataHolder) => new MacroDefinitionRepository(options)
    )

  // formatter options
  val MACRO_DEFINITIONS_PLACEMENT:  DataKey[ElementPlacement]     = new DataKey[ElementPlacement]("MACRO_DEFINITIONS_PLACEMENT", ElementPlacement.AS_IS)
  val MACRO_DEFINITIONS_SORT:       DataKey[ElementPlacementSort] = new DataKey[ElementPlacementSort]("MACRO_DEFINITIONS_SORT", ElementPlacementSort.AS_IS)
  val SOURCE_WRAP_MACRO_REFERENCES: DataKey[Boolean]              = new DataKey[Boolean]("SOURCE_WRAP_MACRO_REFERENCES", false)

  def create(): MacrosExtension = new MacrosExtension()
}
