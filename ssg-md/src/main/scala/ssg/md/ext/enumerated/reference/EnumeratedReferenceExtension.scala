/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferenceExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference

import ssg.md.ext.enumerated.reference.internal.*
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{DataHolder, DataKey, MutableDataHolder}
import ssg.md.util.format.options.{ElementPlacement, ElementPlacementSort}

import scala.language.implicitConversions

/**
 * Extension for enumerated_references
 *
 * Create it with [[EnumeratedReferenceExtension.create]] and then configure it on the builders
 *
 * The parsed enumerated_reference text is turned into [[EnumeratedReferenceText]] nodes.
 */
class EnumeratedReferenceExtension private () extends Parser.ParserExtension,
  HtmlRenderer.HtmlRendererExtension,
  Parser.ReferenceHoldingExtension,
  Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def transferReferences(document: MutableDataHolder, included: DataHolder): Boolean = {
    if (document.contains(EnumeratedReferenceExtension.ENUMERATED_REFERENCES) && included.contains(EnumeratedReferenceExtension.ENUMERATED_REFERENCES)) {
      Parser.transferReferences(
        EnumeratedReferenceExtension.ENUMERATED_REFERENCES.get(document),
        EnumeratedReferenceExtension.ENUMERATED_REFERENCES.get(included),
        EnumeratedReferenceExtension.ENUMERATED_REFERENCES_KEEP.get(document) == KeepType.FIRST
      )
    } else {
      false
    }
  }

  override def extend(parserBuilder: Parser.Builder): Unit = {
    //parserBuilder.paragraphPreProcessorFactory(EnumeratedReferenceParagraphPreProcessor.Factory())
    parserBuilder.postProcessorFactory(new EnumeratedReferenceNodePostProcessor.Factory())
    parserBuilder.customBlockParserFactory(new EnumeratedReferenceBlockParser.Factory())
    parserBuilder.linkRefProcessorFactory(new EnumeratedReferenceLinkRefProcessor.Factory())
  }

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit = {
    formatterBuilder.nodeFormatterFactory(new EnumeratedReferenceNodeFormatter.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new EnumeratedReferenceNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object EnumeratedReferenceExtension {
  val ENUMERATED_REFERENCES_KEEP: DataKey[KeepType] = new DataKey[KeepType]("ENUMERATED_REFERENCES_KEEP", KeepType.FIRST) // standard option to allow control over how to handle duplicates
  val ENUMERATED_REFERENCES: DataKey[EnumeratedReferenceRepository] = new DataKey[EnumeratedReferenceRepository]("ENUMERATED_REFERENCES", new EnumeratedReferenceRepository(null), (options: DataHolder) => new EnumeratedReferenceRepository(options)) // @nowarn - initial value uses null for DataKey pattern
  val ENUMERATED_REFERENCE_ORDINALS: DataKey[EnumeratedReferences] = new DataKey[EnumeratedReferences]("ENUMERATED_REFERENCE_ORDINALS", new EnumeratedReferences(null), (options: DataHolder) => new EnumeratedReferences(options)) // @nowarn - initial value uses null for DataKey pattern

  // formatter options
  val ENUMERATED_REFERENCE_PLACEMENT: DataKey[ElementPlacement] = new DataKey[ElementPlacement]("ENUMERATED_REFERENCE_PLACEMENT", ElementPlacement.AS_IS)
  val ENUMERATED_REFERENCE_SORT: DataKey[ElementPlacementSort] = new DataKey[ElementPlacementSort]("ENUMERATED_REFERENCE_SORT", ElementPlacementSort.AS_IS)

  def create(): EnumeratedReferenceExtension = new EnumeratedReferenceExtension()
}
