/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableSection.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableSection.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

import ssg.md.Nullable

import java.util.ArrayList

class TableSection(val sectionType: TableSectionType) {

  val rows:                   ArrayList[TableRow] = new ArrayList[TableRow]()
  private[format] var row:    Int                 = 0
  private[format] var column: Int                 = 0

  def getRows: ArrayList[TableRow] = rows

  def getRow: Int = row

  def getColumn: Int = column

  def nextRow(): Unit = {
    row += 1
    column = 0
  }

  def setCell(row: Int, column: Int, cell: TableCell): Unit =
    expandTo(row).set(column, cell)

  def normalize(): Unit = {
    val iter = rows.iterator()
    while (iter.hasNext)
      iter.next().normalize()
  }

  def expandTo(row: Int): TableRow =
    expandTo(row, Nullable(null))

  def expandTo(row: Int, cell: Nullable[TableCell]): TableRow = {
    while (row >= rows.size()) {
      val tableRow = defaultRow()
      rows.add(tableRow)
    }
    rows.get(row)
  }

  def expandTo(row: Int, column: Int): TableRow =
    expandTo(row, column, Nullable(null))

  def expandTo(row: Int, column: Int, cell: Nullable[TableCell]): TableRow = {
    while (row >= rows.size()) {
      val tableRow = defaultRow()
      tableRow.expandTo(column, cell.getOrElse(null))
      rows.add(tableRow)
    }
    rows.get(row).expandTo(column)
  }

  def defaultRow(): TableRow =
    new TableRow()

  def defaultCell(): TableCell =
    TableCell.NULL

  def get(row: Int): TableRow =
    expandTo(row, Nullable(null))

  def getMaxColumns: Int = {
    var columns = 0
    val iter    = rows.iterator()
    while (iter.hasNext) {
      val spans = iter.next().getSpannedColumns
      if (columns < spans) columns = spans
    }
    columns
  }

  def getMinColumns: Int = {
    var columns = 0
    val iter    = rows.iterator()
    while (iter.hasNext) {
      val spans = iter.next().getSpannedColumns
      if (columns > spans || columns == 0) columns = spans
    }
    columns
  }

  private def dumpRows(): CharSequence = {
    val sb   = new StringBuilder()
    val iter = rows.iterator()
    while (iter.hasNext)
      sb.append("  ").append(iter.next().toString).append("\n")
    sb
  }

  override def toString: String =
    // NOTE: show not simple name but name of container class if any
    this.getClass.getName.substring(getClass.getPackage.getName.length + 1) + "[" +
      "sectionType=" + sectionType +
      ", rows=[\n" + dumpRows() +
      ']'
}
