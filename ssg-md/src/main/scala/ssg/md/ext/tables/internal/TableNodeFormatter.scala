/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/internal/TableNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/internal/TableNodeFormatter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package tables
package internal

import ssg.md.Nullable
import ssg.md.ast.{ Paragraph, Text }
import ssg.md.formatter.*
import ssg.md.util.data.DataHolder
import ssg.md.util.html.CellAlignment
import ssg.md.util.sequence.{ BasedSequence, LineAppendable }

import scala.language.implicitConversions

import ssg.md.util.format.{ MarkdownTable, TableFormatOptions }

class TableNodeFormatter(options: DataHolder) extends NodeFormatter {

  private val formatOptions:            TableFormatOptions      = new TableFormatOptions(options)
  private val parserTrimCellWhiteSpace: Boolean                 = TablesExtension.TRIM_CELL_WHITESPACE.get(options)
  private var myTable:                  Nullable[MarkdownTable] = Nullable.empty

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[TableBlock](classOf[TableBlock], (node, ctx, md) => renderBlock(node, ctx, md)),
        new NodeFormattingHandler[TableHead](classOf[TableHead], (node, ctx, md) => renderHead(node, ctx, md)),
        new NodeFormattingHandler[TableSeparator](classOf[TableSeparator], (node, ctx, md) => renderSeparator(node, ctx, md)),
        new NodeFormattingHandler[TableBody](classOf[TableBody], (node, ctx, md) => renderBody(node, ctx, md)),
        new NodeFormattingHandler[TableRow](classOf[TableRow], (node, ctx, md) => renderRow(node, ctx, md)),
        new NodeFormattingHandler[TableCell](classOf[TableCell], (node, ctx, md) => renderCell(node, ctx, md)),
        new NodeFormattingHandler[TableCaption](classOf[TableCaption], (node, ctx, md) => renderCaption(node, ctx, md)),
        new NodeFormattingHandler[Text](classOf[Text], (node, ctx, md) => renderText(node, ctx, md))
      )
    )

  private def renderBlock(node: TableBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    myTable = Nullable(new MarkdownTable(node.chars, formatOptions))

    context.getRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS | RenderPurpose.TRANSLATED_SPANS | RenderPurpose.TRANSLATED =>
        markdown.blankLine()
        context.renderChildren(node)
        markdown.tailBlankLine()

      case _ => // FORMAT and default
        context.renderChildren(node)

        val trackedOffsets      = context.getTrackedOffsets
        val tableTrackedOffsets = trackedOffsets.getTrackedOffsets(node.startOffset, node.endOffset)

        if (!trackedOffsets.isEmpty) {
          val iter = tableTrackedOffsets.iterator()
          while (iter.hasNext) {
            val trackedOffset = iter.next()
            assert(trackedOffset.offset >= node.startOffset && trackedOffset.offset <= node.endOffset)
            myTable.foreach(_.addTrackedOffset(trackedOffset))
          }
        }

        // allow table manipulation, mostly for testing
        if (formatOptions.tableManipulator != ssg.md.util.format.TableManipulator.NULL) {
          myTable.foreach { t =>
            t.normalize()
            formatOptions.tableManipulator.apply(t, node)
          }
        }

        myTable.foreach { t =>
          if (t.getMaxColumns > 0) {
            // output table
            markdown.blankLine()

            val prefix = markdown.getPrefix
            t.formatTableIndentPrefix = prefix
            val formattedTable = new MarkdownWriter(markdown.getOptions)
            t.appendTable(formattedTable)

            val tableOffsets = t.getTrackedOffsets
            val startOffset  = markdown.offsetWithPending()
            if (!tableTrackedOffsets.isEmpty) {
              assert(tableTrackedOffsets.size() == tableOffsets.size())

              val iter = tableTrackedOffsets.iterator()
              while (iter.hasNext) {
                val trackedOffset = iter.next()
                assert(trackedOffset.offset >= node.startOffset && trackedOffset.offset <= node.endOffset)

                if (trackedOffset.isResolved) {
                  trackedOffset.setIndex(trackedOffset.getIndex + startOffset)
                }
              }
            }

            markdown.pushPrefix().setPrefix("", false).pushOptions().removeOptions(LineAppendable.F_WHITESPACE_REMOVAL).append(formattedTable).popOptions().popPrefix(false)

            markdown.tailBlankLine()

            if (t.getMaxColumns > 0 && !tableTrackedOffsets.isEmpty) {
              if (formatOptions.dumpIntellijOffsets) {
                markdown.append("\nTracked Offsets").line() // simulate flex example ast dump
                var sep     = "  "
                var i       = 0
                val offIter = tableOffsets.iterator()
                while (offIter.hasNext) {
                  val trackedOffset = offIter.next()
                  i += 1
                  markdown.append(sep).append(s"$i:[${trackedOffset.getIndex},${trackedOffset.getIndex + 1}] was:[${trackedOffset.offset},${trackedOffset.offset + 1}]")
                  sep = " "
                }
                markdown.append("\n")
              }
            }
          }
        }
    }

    myTable = Nullable.empty
  }

  private def renderHead(node: TableHead, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    myTable.foreach { t =>
      t.setSeparator(false)
      t.setHeader(true)
    }
    context.renderChildren(node)
  }

  private def renderSeparator(node: TableSeparator, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    myTable.foreach(_.setSeparator(true))
    context.renderChildren(node)
  }

  private def renderBody(node: TableBody, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    myTable.foreach { t =>
      t.setSeparator(false)
      t.setHeader(false)
    }
    context.renderChildren(node)
  }

  private def renderRow(node: TableRow, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    context.renderChildren(node)
    if (context.getRenderPurpose == RenderPurpose.FORMAT) {
      myTable.foreach { t =>
        if (!t.isSeparator) t.nextRow()
      }
    } else {
      markdown.line()
    }
  }

  private def renderCaption(node: TableCaption, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (context.getRenderPurpose == RenderPurpose.FORMAT) {
      myTable.foreach(_.setCaptionWithMarkers(node, node.openingMarker, node.text, node.closingMarker))
    } else {
      // HACK: to reuse the table formatting logic of MarkdownTable
      val dummyCaption     = if (node.hasChildren) "dummy" else ""
      val formattedCaption = MarkdownTable.formattedCaption(BasedSequence.of(dummyCaption).subSequence(0, dummyCaption.length), formatOptions)

      if (formattedCaption.isDefined) {
        markdown.line().append(node.openingMarker)
        context.renderChildren(node)
        markdown.append(node.closingMarker).line()
      }
    }

  private def renderCell(node: TableCell, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (context.getRenderPurpose == RenderPurpose.FORMAT) {
      var text = node.text
      if (formatOptions.trimCellWhitespace) {
        if (text.isBlank() && !text.isEmpty) {
          text = text.subSequence(0, 1)
        } else {
          text = text.trim()
        }
      }
      val cellAlignment = node.getAlignment.fold(CellAlignment.NONE)(_.cellAlignment)
      myTable.foreach(_.addCell(new ssg.md.util.format.TableCell(node, node.openingMarker, text, node.closingMarker, 1, node.span, cellAlignment)))
    } else {
      if (node.previous.isEmpty) {
        if (formatOptions.leadTrailPipes && node.openingMarker.isEmpty) markdown.append('|')
        else markdown.append(node.openingMarker)
      } else {
        markdown.append(node.openingMarker)
      }

      myTable.foreach { t =>
        if (!t.isSeparator && formatOptions.spaceAroundPipes && (!node.text.startsWith(" ") || parserTrimCellWhiteSpace)) markdown.append(' ')
      }

      val childText = Array("")

      context.translatingSpan { (context1, writer) =>
        context1.renderChildren(node)
        childText(0) = writer.toString(-1, -1)
      }

      myTable.foreach { t =>
        if (!t.isSeparator && formatOptions.spaceAroundPipes && (!childText(0).endsWith(" ") || parserTrimCellWhiteSpace)) markdown.append(' ')
      }

      if (node.next.isEmpty) {
        if (formatOptions.leadTrailPipes && node.closingMarker.isEmpty) markdown.append('|')
        else markdown.append(node.closingMarker)
      } else {
        markdown.append(node.closingMarker)
      }
    }

  private def renderText(node: Text, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (myTable.exists(_.isSeparator)) {
      val parent = node.ancestorOfType(classOf[Paragraph])
      if (parent.isDefined && parent.get.isInstanceOf[Paragraph] && parent.get.asInstanceOf[Paragraph].hasTableSeparator) {
        markdown.pushPrefix().addPrefix(" ").append(node.chars).popPrefix()
      } else {
        markdown.append(node.chars)
      }
    } else {
      markdown.append(node.chars)
    }
}

object TableNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new TableNodeFormatter(options)
  }
}
