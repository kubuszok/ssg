/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacrosNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacrosNodeFormatter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package macros
package internal

import ssg.md.Nullable
import ssg.md.ast.Paragraph
import ssg.md.formatter.*
import ssg.md.util.data.{ DataHolder, DataKey }
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import scala.language.implicitConversions

class MacrosNodeFormatter(options: DataHolder)
    extends NodeRepositoryFormatter[MacroDefinitionRepository, MacroDefinitionBlock, MacroReference](
      options,
      MacrosNodeFormatter.MACROS_TRANSLATION_MAP,
      MacrosNodeFormatter.MACROS_UNIQUIFICATION_MAP
    ) {

  private val macroFormatOptions = new MacroFormatOptions(options)

  override def getRepository(options: DataHolder): MacroDefinitionRepository =
    MacrosExtension.MACRO_DEFINITIONS.get(options)

  override def getReferencePlacement: ElementPlacement = macroFormatOptions.macrosPlacement

  override def getReferenceSort: ElementPlacementSort = macroFormatOptions.macrosSort

  override def renderReferenceBlock(node: MacroDefinitionBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine().append(">>>").append(transformReferenceId(node.name.toString, context)).line()
    val child = node.firstChild
    if (child.isDefined && child.get.isInstanceOf[Paragraph] && child == node.lastChild) {
      // if a single paragraph then we unwrap it and output only its children as inline text
      context.renderChildren(child.get)
    } else {
      context.renderChildren(node)
    }
    markdown.line().append("<<<").blankLine()
  }

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[MacroReference](classOf[MacroReference], (node, ctx, md) => renderMacroReference(node, ctx, md)),
        new NodeFormattingHandler[MacroDefinitionBlock](classOf[MacroDefinitionBlock], (node, ctx, md) => renderMacroDefinition(node, ctx, md))
      )
    )

  override def getNodeClasses: Nullable[Set[Class[?]]] =
    if (macroFormatOptions.macrosPlacement.isNoChange || !macroFormatOptions.macrosSort.isUnused) Nullable.empty
    else {
      Nullable(Set[Class[?]](classOf[MacroReference]))
    }

  private def renderMacroDefinition(node: MacroDefinitionBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderReference(node, context, markdown)

  private def renderMacroReference(node: MacroReference, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append("<<<")
    if (context.isTransformingText) {
      val referenceId = transformReferenceId(node.text.toString, context)
      context.nonTranslatingSpan((context1, markdown1) => markdown1.append(referenceId))
    } else {
      markdown.append(node.text)
    }
    markdown.append(">>>")
  }
}

object MacrosNodeFormatter {
  val MACROS_TRANSLATION_MAP:    DataKey[java.util.Map[String, String]] = new DataKey[java.util.Map[String, String]]("MACROS_TRANSLATION_MAP", new java.util.HashMap[String, String]())
  val MACROS_UNIQUIFICATION_MAP: DataKey[java.util.Map[String, String]] =
    new DataKey[java.util.Map[String, String]]("MACROS_UNIQUIFICATION_MAP", new java.util.HashMap[String, String]()) // uniquified references

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new MacrosNodeFormatter(options)
  }
}
