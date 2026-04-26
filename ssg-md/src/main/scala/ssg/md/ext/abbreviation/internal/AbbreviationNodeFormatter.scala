/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationNodeFormatter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package abbreviation
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.data.{ DataHolder, DataKey }
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import java.{ util => ju }
import scala.language.implicitConversions

class AbbreviationNodeFormatter(options: DataHolder)
    extends NodeRepositoryFormatter[AbbreviationRepository, AbbreviationBlock, Abbreviation](
      options,
      AbbreviationNodeFormatter.ABBREVIATION_TRANSLATION_MAP,
      AbbreviationNodeFormatter.ABBREVIATION_UNIQUIFICATION_MAP
    ) {

  private val formatOptions:        AbbreviationFormatOptions = new AbbreviationFormatOptions(options)
  private val transformUnderscores: Boolean                   = {
    val transformedId = String.format(Formatter.TRANSLATION_ID_FORMAT.get(options), 1: Integer)
    transformedId.startsWith("_") && transformedId.endsWith("_")
  }
  private val makeMergedAbbreviationsUnique: Boolean = AbbreviationExtension.MAKE_MERGED_ABBREVIATIONS_UNIQUE.get(options)

  override protected def makeReferencesUnique: Boolean = makeMergedAbbreviationsUnique

  override def getRepository(options: DataHolder): AbbreviationRepository = AbbreviationExtension.ABBREVIATIONS.get(options)

  override def getReferencePlacement: ElementPlacement = formatOptions.abbreviationsPlacement

  override def getReferenceSort: ElementPlacementSort = formatOptions.abbreviationsSort

  override def modifyTransformedReference(transformedText: String, context: NodeFormatterContext): String = {
    var result = transformedText
    if (transformUnderscores && context.isTransformingText) {
      if (result.startsWith("-") && result.endsWith("-")) {
        result = "_" + result.substring(1, result.length - 1) + "_"
      } else if (result.startsWith("_") && result.endsWith("_")) {
        result = "-" + result.substring(1, result.length - 1) + "-"
      }
    }
    result
  }

  override def renderReferenceBlock(node: AbbreviationBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    markdown.append(transformReferenceId(node.text.toString, context))
    markdown.append(node.closingMarker).append(' ')
    markdown.append(node.abbreviation).line()
  }

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[Abbreviation](classOf[Abbreviation], (node, ctx, md) => renderAbbreviation(node, ctx, md)),
        new NodeFormattingHandler[AbbreviationBlock](classOf[AbbreviationBlock], (node, ctx, md) => renderAbbreviationBlock(node, ctx, md))
      )
    )

  override def getNodeClasses: Nullable[Set[Class[?]]] =
    if (formatOptions.abbreviationsPlacement.isNoChange || !formatOptions.abbreviationsSort.isUnused) Nullable.empty
    else Nullable(Set[Class[?]](classOf[Abbreviation]))

  private def renderAbbreviationBlock(node: AbbreviationBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderReference(node, context, markdown)

  private def renderAbbreviation(node: Abbreviation, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (context.isTransformingText) {
      val referenceId = transformReferenceId(node.chars.toString, context)
      markdown.append(referenceId)
    } else {
      markdown.append(node.chars)
    }
}

object AbbreviationNodeFormatter {

  val ABBREVIATION_TRANSLATION_MAP:    DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]]("ABBREVIATION_TRANSLATION_MAP", new ju.HashMap[String, String]())
  val ABBREVIATION_UNIQUIFICATION_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]]("ABBREVIATION_UNIQUIFICATION_MAP", new ju.HashMap[String, String]())

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new AbbreviationNodeFormatter(options)
  }
}
