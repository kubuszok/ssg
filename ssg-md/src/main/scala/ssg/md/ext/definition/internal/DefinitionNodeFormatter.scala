/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package definition
package internal

import ssg.md.Nullable
import ssg.md.ast.Paragraph
import ssg.md.formatter.*
import ssg.md.parser.{ ListOptions, Parser }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.{ BasedSequence, RepeatedSequence }

import scala.language.implicitConversions

class DefinitionNodeFormatter(options: DataHolder) extends NodeFormatter {

  private val formatOptions: DefinitionFormatOptions = new DefinitionFormatOptions(options)
  private val listOptions:   ListOptions             = ListOptions.get(options)

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[DefinitionList](classOf[DefinitionList], (node, ctx, md) => renderList(node, ctx, md)),
        new NodeFormattingHandler[DefinitionTerm](classOf[DefinitionTerm], (node, ctx, md) => renderTerm(node, ctx, md)),
        new NodeFormattingHandler[DefinitionItem](classOf[DefinitionItem], (node, ctx, md) => renderItem(node, ctx, md))
      )
    )

  private def renderList(node: DefinitionList, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    context.renderChildren(node)

  private def renderTerm(node: DefinitionTerm, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    context.renderChildren(node)

  private def renderItem(node: DefinitionItem, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    val openMarkerChars  = node.chars.prefixOf(node.firstChild.get.chars)
    var openMarker       = openMarkerChars.subSequence(0, 1)
    var openMarkerSpaces = openMarkerChars.subSequence(1)

    if (formatOptions.markerSpaces >= 1 && openMarkerSpaces.length() != formatOptions.markerSpaces) {
      val charSequence = RepeatedSequence.repeatOf(' ', formatOptions.markerSpaces)
      openMarkerSpaces = BasedSequence.of(charSequence)
    }

    formatOptions.markerType match {
      case ssg.md.util.format.options.DefinitionMarker.ANY   => ()
      case ssg.md.util.format.options.DefinitionMarker.COLON =>
        openMarker = BasedSequence.of(":").subSequence(0, 1)
      case ssg.md.util.format.options.DefinitionMarker.TILDE =>
        openMarker = BasedSequence.of("~").subSequence(0, 1)
    }

    markdown.line().append(openMarker).append(openMarkerSpaces)
    val count  = if (context.getFormatterOptions.itemContentIndent) openMarker.length() + openMarkerSpaces.length() else listOptions.getItemIndent
    val prefix = RepeatedSequence.ofSpaces(count)
    markdown.pushPrefix().addPrefix(prefix)
    context.renderChildren(node)
    markdown.popPrefix()

    if (!Parser.BLANK_LINES_IN_AST.get(context.getOptions)) {
      // add blank lines after last paragraph item
      node.lastChild.foreach {
        case p: Paragraph if p.trailingBlankLine => markdown.blankLine()
        case _ => ()
      }
    }
  }
}

object DefinitionNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new DefinitionNodeFormatter(options)
  }
}
