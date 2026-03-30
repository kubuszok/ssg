/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableExtractingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables

import ssg.md.Nullable
import ssg.md.util.ast.{Node, NodeVisitor, VisitHandler}
import ssg.md.util.data.DataHolder
import ssg.md.util.format.{MarkdownTable, TableFormatOptions}
import ssg.md.util.html.CellAlignment

import scala.language.implicitConversions

import java.util.{ArrayList, List as JList}

class TableExtractingVisitor(options: DataHolder) {

  private val formatOptions: TableFormatOptions = new TableFormatOptions(options)

  private val myVisitor: NodeVisitor = new NodeVisitor(
    new VisitHandler[TableBlock](classOf[TableBlock], visitTableBlock(_)),
    new VisitHandler[TableHead](classOf[TableHead], visitTableHead(_)),
    new VisitHandler[TableSeparator](classOf[TableSeparator], visitTableSeparator(_)),
    new VisitHandler[TableBody](classOf[TableBody], visitTableBody(_)),
    new VisitHandler[TableRow](classOf[TableRow], visitTableRow(_)),
    new VisitHandler[TableCell](classOf[TableCell], visitTableCell(_)),
    new VisitHandler[TableCaption](classOf[TableCaption], visitTableCaption(_)),
  )

  private var myTable: Nullable[MarkdownTable] = Nullable.empty
  private val myTables: JList[MarkdownTable] = new ArrayList[MarkdownTable]()

  def getTables(node: Node): Array[MarkdownTable] = {
    myTable = Nullable.empty
    myVisitor.visit(node)
    myTables.toArray(new Array[MarkdownTable](0))
  }

  private def visitTableBlock(node: TableBlock): Unit = {
    myTable = Nullable(new MarkdownTable(node.chars, formatOptions))
    myVisitor.visitChildren(node)
    myTable.foreach(myTables.add)
    myTable = Nullable.empty
  }

  private def visitTableHead(node: TableHead): Unit = {
    myTable.foreach { t =>
      t.setSeparator(false)
      t.setHeader(true)
    }
    myVisitor.visitChildren(node)
  }

  private def visitTableSeparator(node: TableSeparator): Unit = {
    myTable.foreach(_.setSeparator(true))
    myVisitor.visitChildren(node)
  }

  private def visitTableBody(node: TableBody): Unit = {
    myTable.foreach { t =>
      t.setSeparator(false)
      t.setHeader(false)
    }
    myVisitor.visitChildren(node)
  }

  private def visitTableRow(node: TableRow): Unit = {
    myVisitor.visitChildren(node)
    myTable.foreach { t =>
      if (!t.isSeparator) t.nextRow()
    }
  }

  private def visitTableCaption(node: TableCaption): Unit = {
    myTable.foreach(_.setCaptionWithMarkers(node, node.openingMarker, node.text, node.closingMarker))
  }

  private def visitTableCell(node: TableCell): Unit = {
    myTable.foreach { t =>
      var text = node.text
      if (formatOptions.trimCellWhitespace) {
        if (text.isBlank() && !text.isEmpty) {
          text = text.subSequence(0, 1)
        } else {
          text = text.trim()
        }
      }
      val cellAlignment = node.getAlignment.fold(CellAlignment.NONE)(_.cellAlignment)
      t.addCell(new ssg.md.util.format.TableCell(node, node.openingMarker, text, node.closingMarker, 1, node.span, cellAlignment))
    }
  }
}

