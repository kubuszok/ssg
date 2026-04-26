/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCellOffsetInfo.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCellOffsetInfo.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format

import ssg.md.Nullable
import ssg.md.util.collection.{ BoundedMaxAggregator, BoundedMinAggregator }
import ssg.md.util.misc.Utils.{ maxLimit, minLimit }

import java.util
import java.util.{ HashMap, Map as JMap }
import java.util.function.BiFunction

class TableCellOffsetInfo(
  val offset:       Int,
  val table:        MarkdownTable,
  val section:      TableSection,
  val tableRow:     Nullable[TableRow],
  val tableCell:    Nullable[TableCell],
  val row:          Int,
  val column:       Int,
  val insideColumn: Nullable[Integer],
  val insideOffset: Nullable[Integer]
) {

  def isCaptionLine: Boolean =
    tableRow.isDefined && tableRow.get.isInstanceOf[TableCaptionRow] && (section eq table.caption)

  def isSeparatorLine: Boolean =
    section.sectionType == TableSectionType.SEPARATOR

  def isInsideCaption: Boolean =
    isCaptionLine && getInsideColumn

  def isAfterCaption: Boolean =
    isCaptionLine && isAfterCells

  def isBeforeCaption: Boolean =
    isCaptionLine && isBeforeCells

  def isInsideCell: Boolean =
    tableRow.isDefined && tableCell.isDefined && insideColumn.isDefined

  def getInsideColumn: Boolean =
    insideColumn.isDefined

  def isBeforeCells: Boolean =
    tableRow.isDefined && tableCell.isDefined && insideColumn.isEmpty && column < tableRow.get.cells.size() && offset <= tableCell.get.getStartOffset(getPreviousCell)

  def getPreviousCell: Nullable[TableCell] =
    getPreviousCell(1)

  def getPreviousCell(offset: Int): Nullable[TableCell] =
    getPreviousCell(tableRow, offset)

  def getPreviousCell(tableRow: Nullable[TableRow], offset: Int): Nullable[TableCell] =
    if (column >= offset && tableRow.isDefined) Nullable(tableRow.get.cells.get(column - offset))
    else Nullable(null)

  def isInCellSpan: Boolean =
    tableRow.isDefined && tableCell.isDefined && insideColumn.isEmpty && offset >= tableCell.get.getStartOffset(getPreviousCell) && offset < tableCell.get.getEndOffset

  def isAfterCells: Boolean =
    tableRow.isDefined && tableCell.isDefined && insideColumn.isEmpty && column == tableRow.get.cells.size() && offset >= tableCell.get.getEndOffset

  def canDeleteColumn: Boolean =
    insideColumn.isDefined && table.getMinColumnsWithoutColumns(true, column) > 0

  def canDeleteRow: Boolean =
    tableRow.isDefined && (section ne table.separator) && table.body.rows.size() + table.header.rows.size() > 1

  def isFirstCell: Boolean =
    getInsideColumn && column == 0

  def isLastCell: Boolean =
    getInsideColumn && tableRow.isDefined && column + 1 == tableRow.get.cells.size()

  def isLastRow: Boolean =
    row + 1 == table.getAllRowsCount

  /** Only available if inside are set and not in first cell of first row <p> CAUTION: NOT TESTED
    *
    * @param insideOffset
    *   offset inside the cell, null if same as the current cell inside offset
    * @return
    *   offset in previous cell or null
    */
  def previousCellOffset(insideOffset: Nullable[Integer]): Nullable[TableCellOffsetInfo] =
    if (getInsideColumn && column > 0) {
      val cell         = getPreviousCell
      val previousCell = getPreviousCell(2)
      if (insideOffset.isEmpty) {
        cell.get.textToInsideOffset(
          tableCell.get.insideToTextOffset(if (this.insideOffset.isEmpty) 0 else this.insideOffset.get.intValue(), previousCell),
          previousCell
        )
      }
      Nullable(
        table.getCellOffsetInfo(
          cell.get.getTextStartOffset(previousCell) + maxLimit(cell.get.getCellSize(previousCell), minLimit(0, insideOffset.get.intValue()))
        )
      )
    } else {
      Nullable(null)
    }

  /** Only available if tableRow/tableCell are set and not in first cell of first row <p> CAUTION: NOT TESTED
    *
    * @param insideOffset
    *   offset inside the cell, null if same as th
    * @return
    *   offset in previous cell or null
    */
  def nextCellOffset(insideOffset: Nullable[Integer]): Nullable[TableCellOffsetInfo] =
    if (getInsideColumn && tableRow.isDefined && column + 1 < tableRow.get.cells.size()) {
      val cell         = getPreviousCell
      val previousCell = getPreviousCell(2)
      if (insideOffset.isEmpty) {
        cell.get.textToInsideOffset(
          tableCell.get.insideToTextOffset(if (this.insideOffset.isEmpty) 0 else this.insideOffset.get.intValue(), previousCell),
          previousCell
        )
      }
      Nullable(
        table.getCellOffsetInfo(
          cell.get.getTextStartOffset(previousCell) + maxLimit(cell.get.getCellSize(previousCell), minLimit(0, insideOffset.get.intValue()))
        )
      )
    } else {
      Nullable(null)
    }

  /** Only available if not at row 0 <p> CAUTION: NOT TESTED
    *
    * @param insideOffset
    *   offset inside the cell, null if same as th
    * @return
    *   offset in previous cell or null
    */
  def previousRowOffset(insideOffset: Nullable[Integer]): Nullable[TableCellOffsetInfo] =
    if (row > 0) {
      val allRows  = table.getAllRows
      val otherRow = allRows.get(this.row - 1)
      if (getInsideColumn && column < otherRow.cells.size()) {
        // transfer inside offset
        val cell         = getPreviousCell
        val previousCell = getPreviousCell(2)
        if (insideOffset.isEmpty) {
          cell.get.textToInsideOffset(
            tableCell.get.insideToTextOffset(if (this.insideOffset.isEmpty) 0 else this.insideOffset.get.intValue(), previousCell),
            previousCell
          )
        }
        Nullable(
          table.getCellOffsetInfo(
            cell.get.getTextStartOffset(previousCell) + maxLimit(cell.get.getCellSize(previousCell), minLimit(0, insideOffset.get.intValue()))
          )
        )
      } else {
        if (isBeforeCells) {
          Nullable(table.getCellOffsetInfo(otherRow.cells.get(0).getStartOffset(Nullable(null))))
        } else {
          Nullable(table.getCellOffsetInfo(otherRow.cells.get(otherRow.cells.size() - 1).getEndOffset))
        }
      }
    } else {
      Nullable(null)
    }

  /** Only available if not at last row <p> CAUTION: NOT TESTED
    *
    * @param insideOffset
    *   offset inside the cell, null if same as th
    * @return
    *   offset in previous cell or null
    */
  def nextRowOffset(insideOffset: Nullable[Integer]): Nullable[TableCellOffsetInfo] =
    if (row + 1 < table.getAllRowsCount) {
      val allRows  = table.getAllRows
      val otherRow = allRows.get(this.row + 1)
      if (getInsideColumn && column < otherRow.cells.size()) {
        // transfer inside offset
        val cell         = Nullable(otherRow.cells.get(column))
        val previousCell = getPreviousCell(Nullable(otherRow), 1)
        if (insideOffset.isEmpty) {
          cell.get.textToInsideOffset(
            tableCell.get.insideToTextOffset(if (this.insideOffset.isEmpty) 0 else this.insideOffset.get.intValue(), previousCell),
            previousCell
          )
        }
        Nullable(
          table.getCellOffsetInfo(
            cell.get.getTextStartOffset(previousCell) + maxLimit(cell.get.getCellSize(previousCell), minLimit(0, insideOffset.get.intValue()))
          )
        )
      } else {
        if (isBeforeCells) {
          Nullable(table.getCellOffsetInfo(otherRow.cells.get(0).getStartOffset(Nullable(null))))
        } else {
          Nullable(table.getCellOffsetInfo(otherRow.cells.get(otherRow.cells.size() - 1).getEndOffset))
        }
      }
    } else {
      Nullable(null)
    }

  /** Available if somewhere in table
    *
    * @param stopPointsMap
    *   stop points of interest map by section or null
    * @return
    *   next stop point offset or offset after end of table
    */
  def nextOffsetStop(stopPointsMap: Nullable[JMap[TableSectionType, Integer]]): TableCellOffsetInfo = {
    val stopOffset = TableCellOffsetInfo.getStopOffset(offset, table, stopPointsMap, nextOffset = true)
    if (stopOffset != -1) {
      table.getCellOffsetInfo(stopOffset)
    } else {
      // go to after the table
      val allRows      = table.getAllSectionRows
      val lastRow      = allRows.get(allRows.size() - 1)
      val lastCell     = lastRow.cells.get(lastRow.cells.size() - 1)
      val lastOffset   = lastCell.getEndOffset
      val baseSequence = lastCell.text.getBaseSequence

      val eolPos = baseSequence.endOfLineAnyEOL(lastOffset)
      table.getCellOffsetInfo(if (eolPos == -1) lastOffset else eolPos + baseSequence.eolStartLength(eolPos))
    }
  }

  /** Available if somewhere in table
    *
    * @param stopPointsMap
    *   stop points of interest map by section or null for default
    * @return
    *   previous stop point offset or start of table offset
    */
  def previousOffsetStop(stopPointsMap: Nullable[JMap[TableSectionType, Integer]]): TableCellOffsetInfo = {
    val stopOffset = TableCellOffsetInfo.getStopOffset(offset, table, stopPointsMap, nextOffset = false)
    if (stopOffset != -1) {
      table.getCellOffsetInfo(stopOffset)
    } else {
      table.getCellOffsetInfo(table.getTableStartOffset)
    }
  }

  override def toString: String =
    "CellOffsetInfo{" +
      " offset=" + offset +
      ", row=" + row +
      ", column=" + column +
      ", insideColumn=" + insideColumn +
      ", insideOffset=" + insideOffset +
      '}'
}

object TableCellOffsetInfo {

  // Stop points used by next/prev tab navigation
  val ROW_START:  Int = 0x0001
  val TEXT_START: Int = 0x0002
  val TEXT_END:   Int = 0x0004
  val ROW_END:    Int = 0x0008

  private val DEFAULT_STOP_POINTS_MAP: HashMap[TableSectionType, Integer] = {
    val m = new HashMap[TableSectionType, Integer]()
    m.put(TableSectionType.HEADER, TEXT_END)
    m.put(TableSectionType.SEPARATOR, TEXT_START | TEXT_END)
    m.put(TableSectionType.BODY, TEXT_END)
    m.put(TableSectionType.CAPTION, TEXT_END)
    m
  }

  private def haveStopPoint(flags: Int, mask: Int): Boolean =
    (flags & mask) != 0

  private def haveRowStart(flags: Int): Boolean =
    (flags & ROW_START) != 0

  private def haveRowEnd(flags: Int): Boolean =
    (flags & ROW_END) != 0

  private def haveTextStart(flags: Int): Boolean =
    (flags & TEXT_START) != 0

  private def haveTextEnd(flags: Int): Boolean =
    (flags & TEXT_END) != 0

  /** Return the next/previous stop point of interest <p> NOTE: not terribly efficient because it goes through all cells of all rows. Only intended for UI use where this is not an issue since it is
    * done per user key
    *
    * @param offset
    *   current offset
    * @param table
    *   for table
    * @param stopPointsMap
    *   map of stop points by section or null for default
    * @param nextOffset
    *   true if next offset stop point, false for previous stop point of interest
    * @return
    *   stop point found or -1 if not found
    */
  private def getStopOffset(
    offset:        Int,
    table:         MarkdownTable,
    stopPointsMap: Nullable[JMap[TableSectionType, Integer]],
    nextOffset:    Boolean
  ): Int = {
    val result = Array[Nullable[Integer]](Nullable(null))

    val useStopPointsMap: JMap[TableSectionType, Integer]                                     = if (stopPointsMap.isEmpty) DEFAULT_STOP_POINTS_MAP else stopPointsMap.get
    val aggregator:       BiFunction[Nullable[Integer], Nullable[Integer], Nullable[Integer]] =
      if (nextOffset) new BoundedMinAggregator(offset)
      else new BoundedMaxAggregator(offset)

    table.forAllSectionRows(
      new TableRowManipulator {
        override def apply(row: TableRow, allRowsIndex: Int, sectionRows: util.ArrayList[TableRow], sectionRowIndex: Int): Int = {
          val section = table.getAllRowsSection(allRowsIndex)
          if (!row.cells.isEmpty && useStopPointsMap.containsKey(section.sectionType)) {
            val flags = useStopPointsMap.get(section.sectionType).intValue()

            if (flags != 0) {
              val rowStart = row.cells.get(0).getStartOffset(Nullable(null))
              val rowEnd   = row.cells.get(row.cells.size() - 1).getEndOffset

              if (haveRowStart(flags)) {
                result(0) = aggregator.apply(result(0), Nullable(Integer.valueOf(rowStart)))
              }

              if (haveStopPoint(flags, TEXT_START | TEXT_END)) {
                var previousCell: Nullable[TableCell] = Nullable(null)
                var i = 0
                while (i < row.cells.size()) {
                  val cell = row.cells.get(i)
                  if (haveTextStart(flags)) {
                    val textStart = cell.getTextStartOffset(previousCell)
                    result(0) = aggregator.apply(result(0), Nullable(Integer.valueOf(textStart)))
                  }

                  if (haveTextEnd(flags)) {
                    val textEnd = cell.getTextEndOffset(previousCell)
                    result(0) = aggregator.apply(result(0), Nullable(Integer.valueOf(textEnd)))
                  }
                  previousCell = Nullable(cell)
                  i += 1
                }
              }

              if (haveRowEnd(flags)) {
                result(0) = aggregator.apply(result(0), Nullable(Integer.valueOf(rowEnd)))
              }
            }
          }
          0
        }
      }
    )

    if (result(0).isEmpty) -1 else result(0).get.intValue()
  }
}
