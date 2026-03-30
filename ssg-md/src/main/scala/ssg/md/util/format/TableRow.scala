/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableRow.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package format

import ssg.md.Nullable
import ssg.md.util.misc.Utils.{ maxLimit, minLimit }
import ssg.md.util.sequence.PrefixedSubSequence

import java.util.{ ArrayList, List as JList }
import scala.util.boundary
import scala.util.boundary.break

class TableRow {

  private[format] val cells: JList[TableCell] = new ArrayList[TableCell]()
  var beforeOffset:          Int              = TableCell.NOT_TRACKED
  var afterOffset:           Int              = TableCell.NOT_TRACKED
  private var normalized:    Boolean          = true

  def getCells: JList[TableCell] = cells

  def forAllCells(consumer: TableCellConsumer): Unit =
    forAllCells(0, Integer.MAX_VALUE, consumer)

  def forAllCells(startIndex: Int, consumer: TableCellConsumer): Unit =
    forAllCells(startIndex, Integer.MAX_VALUE, consumer)

  def forAllCells(startIndex: Int, count: Int, consumer: TableCellConsumer): Unit =
    forAllCells(
      startIndex,
      count,
      new TableCellManipulator {
        override def apply(cell: TableCell, cellIndex: Int, cellColumn: Int, allCellIndex: Int): Int = {
          consumer.accept(cell, cellIndex, cellColumn)
          0
        }
      }
    )

  def forAllCells(manipulator: TableCellManipulator): Unit =
    forAllCells(0, Integer.MAX_VALUE, manipulator)

  def forAllCells(startIndex: Int, manipulator: TableCellManipulator): Unit =
    forAllCells(startIndex, Integer.MAX_VALUE, manipulator)

  def forAllCells(startIndex: Int, count: Int, manipulator: TableCellManipulator): Unit = {
    var iMax = cells.size()
    if (startIndex < iMax && count > 0) {
      var column        = 0
      var remaining     = count
      var allCellsIndex = 0
      var i             = 0

      boundary {
        while (i < iMax) {
          val cell = cells.get(i)

          if (i >= startIndex) {
            val result = manipulator.apply(cell, i, column, allCellsIndex)

            if (result == TableCellManipulator.BREAK) break()

            if (result < 0) {
              allCellsIndex -= result // adjust for deleted cells
              remaining += result
              iMax += result
            } else {
              i += result + 1
              column += cell.columnSpan
              remaining -= 1
              iMax += result
            }

            allCellsIndex += 1

            if (remaining <= 0) break()
          } else {
            i += 1
            allCellsIndex += 1
            column += cell.columnSpan
          }
        }
      }
    }
  }

  def getColumns: Int = cells.size()

  def getTotalColumns: Int = getSpannedColumns

  def getSpannedColumns: Int = {
    var columns = 0
    var i       = 0
    while (i < cells.size()) {
      val cell = cells.get(i)
      if (cell != null) {
        columns += cell.columnSpan
      }
      i += 1
    }
    columns
  }

  def columnOf(index: Int): Int =
    columnOfOrNull(index)

  def columnOfOrNull(index: Int): Int = {
    var columns = 0
    val iMax    = maxLimit(index, cells.size())
    var i       = 0
    while (i < iMax) {
      val cell = cells.get(i)
      columns += cell.columnSpan
      i += 1
    }
    columns
  }

  def appendColumns(count: Int): Unit =
    appendColumns(count, Nullable(null))

  def appendColumns(count: Int, tableCell: Nullable[TableCell]): Unit = {
    val useCell = if (tableCell.isEmpty || tableCell.get.columnSpan == 0) defaultCell() else tableCell.get
    var i       = 0
    while (i < count) {
      // add empty column
      cells.add(cells.size(), useCell)
      i += 1
    }
  }

  def defaultCell(): TableCell =
    new TableCell(" ", 1, 1)

  def addColumn(index: Int): Unit =
    cells.add(index, defaultCell())

  /** @param column
    *   column index before which to insert
    * @param count
    *   number of columns to insert
    */
  def insertColumns(column: Int, count: Int): Unit =
    insertColumns(column, count, Nullable(null))

  /** NOTE: inserting into a cell span has the effect of expanding the span if the cell text is blank or insert count &gt; 1 or splitting the span if it is not blank and count == 1
    *
    * @param column
    *   column index before which to insert
    * @param count
    *   number of columns to insert
    * @param tableCell
    *   table cell to insert, null for default
    */
  def insertColumns(column: Int, count: Int, tableCell: Nullable[TableCell]): Unit = {
    if (count <= 0 || column < 0) {
      return // NOTE: early return is OK here - trivial guard clause
    }

    normalizeIfNeeded()

    val useCell = if (tableCell.isEmpty || tableCell.get.columnSpan == 0) defaultCell() else tableCell.get

    val totalColumns = this.getTotalColumns
    if (column >= totalColumns) {
      // append to the end
      appendColumns(count, Nullable(useCell))
    } else {
      // insert in the middle
      val indexSpan  = indexOf(column)
      val index      = indexSpan.index
      val spanOffset = indexSpan.spanOffset

      if (spanOffset > 0 && index < cells.size()) {
        // spanning column, we expand its span or split into 2
        val cell = cells.get(index)

        if (useCell.text.isBlank() || count > 1) {
          // expand span
          cells.remove(index)
          cells.add(index, cell.withColumnSpan(cell.columnSpan + count))
        } else {
          // split span into before inserted and after
          cells.remove(index)
          cells.add(index, cell.withColumnSpan(spanOffset))
          cells.add(index + 1, useCell.withColumnSpan(minLimit(1, cell.columnSpan - spanOffset + 1)))
        }
      } else {
        var i = 0
        while (i < count) {
          cells.add(index, useCell)
          i += 1
        }
      }
    }
  }

  /** @param column
    *   column index before which to insert
    * @param count
    *   number of columns to insert
    */
  def deleteColumns(column: Int, count: Int): Unit = {
    if (count <= 0 || column < 0) {
      return // NOTE: trivial guard clause
    }

    normalizeIfNeeded()

    var remaining  = count
    val indexSpan  = indexOf(column)
    var index      = indexSpan.index
    var spanOffset = indexSpan.spanOffset

    boundary {
      while (index < cells.size() && remaining > 0) {
        val cell = cells.get(index)
        cells.remove(index)

        if (spanOffset > 0) {
          // inside the first partial span, truncate it to offset or reduce by remaining
          if (cell.columnSpan - spanOffset > remaining) {
            cells.add(index, cell.withColumnSpan(cell.columnSpan - remaining))
            break()
          } else {
            // reinsert with reduced span
            cells.add(index, cell.withColumnSpan(spanOffset))
            index += 1
          }
        } else if (cell.columnSpan - spanOffset > remaining) {
          // reinsert with reduced span and empty text
          cells.add(index, defaultCell().withColumnSpan(cell.columnSpan - remaining))
          break()
        }

        remaining -= cell.columnSpan - spanOffset
        spanOffset = 0
      }
    }
  }

  def moveColumn(fromColumn: Int, toColumn: Int): Unit = {
    if (fromColumn < 0 || toColumn < 0) {
      return // NOTE: trivial guard clause
    }

    normalizeIfNeeded()

    val maxColumn = getTotalColumns

    if (fromColumn >= maxColumn) {
      return // NOTE: trivial guard clause
    }

    var toCol = toColumn
    if (toCol >= maxColumn) toCol = maxColumn - 1

    if (fromColumn != toCol && toCol < maxColumn) {
      val fromIndexSpan  = indexOf(fromColumn)
      val fromIndex      = fromIndexSpan.index
      val fromSpanOffset = fromIndexSpan.spanOffset
      val cell           = cells.get(fromIndex).withColumnSpan(1)

      val toIndexSpan = indexOf(toCol)
      val toIndex     = toIndexSpan.index

      if (toIndex != fromIndex) {
        if (fromSpanOffset > 0) {
          // from inside the span is same as a blank column
          insertColumns(toCol + (if (fromColumn <= toCol) 1 else 0), 1, Nullable(defaultCell()))
        } else {
          insertColumns(toCol + (if (fromColumn <= toCol) 1 else 0), 1, Nullable(cell.withColumnSpan(1)))
        }
        deleteColumns(fromColumn + (if (toCol <= fromColumn) 1 else 0), 1)
      }
    }
  }

  def expandTo(column: Int): TableRow =
    expandTo(column, TableCell.NULL)

  def expandTo(column: Int, cell: TableCell): TableRow = {
    if (cell == null || cell.columnSpan == 0) normalized = false

    while (column >= cells.size())
      cells.add(cell)
    this
  }

  private[format] def fillMissingColumns(minColumn: Nullable[Integer], maxColumns: Int): Unit = {
    val columns = getSpannedColumns
    if (columns < maxColumns) {
      var columnIndex = if (minColumn.isEmpty) cells.size() else minColumn.get.intValue()
      var count       = maxColumns - columns

      if (minColumn.isEmpty || minColumn.get.intValue() >= columns) {
        columnIndex = cells.size()
      }

      val empty0   = defaultCell()
      var prevCell = if (columnIndex > 0) cells.get(columnIndex - 1) else empty0
      var empty    = empty0

      while (count > 0) {
        count -= 1
        // need to change its text to previous cell's end
        val endOffset = prevCell.getEndOffset
        // diagnostic/3095, text is not the right source for the sequence if closeMarker is not empty
        empty = empty.withText(PrefixedSubSequence.prefixOf(" ", prevCell.getLastSegment.getBaseSequence, endOffset, endOffset))

        cells.add(Math.min(columnIndex, cells.size()), empty)
        prevCell = empty
        columnIndex += 1
      }
    }
  }

  def set(column: Int, cell: TableCell): Unit = {
    expandTo(column, null)
    cells.set(column, cell)
  }

  def isEmptyColumn(column: Int): Boolean = {
    val index = indexOf(column).index
    index >= cells.size() || cells.get(index).text.isBlank()
  }

  def isEmpty: Boolean = {
    var i = 0
    while (i < cells.size()) {
      val cell = cells.get(i)
      if (cell != null && !cell.text.isBlank()) {
        return false // NOTE: trivial early return
      }
      i += 1
    }
    true
  }

  def indexOf(column: Int): MarkdownTable.IndexSpanOffset =
    indexOfOrNull(column)

  def indexOfOrNull(column: Int): MarkdownTable.IndexSpanOffset = {
    var remainingColumns = column
    var index            = 0

    var i = 0
    while (i < cells.size()) {
      val cell = cells.get(i)
      if (cell.columnSpan > remainingColumns) {
        return new MarkdownTable.IndexSpanOffset(index, remainingColumns) // NOTE: early return from search
      }

      remainingColumns -= cell.columnSpan

      if (cell.columnSpan > 0) index += 1
      i += 1
    }

    new MarkdownTable.IndexSpanOffset(index, 0)
  }

  def normalizeIfNeeded(): Unit =
    if (!normalized) {
      normalize()
    }

  def normalize(): Unit = {
    var column = 0
    while (column < cells.size()) {
      val cell = cells.get(column)
      if (cell == null || (cell eq TableCell.NULL)) cells.remove(column)
      else column += 1
    }
    normalized = true
  }

  private def dumpCells(): CharSequence = {
    val sb = new StringBuilder()
    var i  = 0
    while (i < cells.size()) {
      sb.append("    ").append(cells.get(i).toString).append("\n")
      i += 1
    }
    sb
  }

  override def toString: String =
    // NOTE: show not simple name but name of container class if any
    this.getClass.getName.substring(getClass.getPackage.getName.length + 1) + "{" +
      " beforeOffset=" + beforeOffset +
      ", afterOffset=" + afterOffset +
      ", normalized=" + normalized +
      ", cells=[\n" + dumpCells() + "    ]\n" +
      "  }"
}
