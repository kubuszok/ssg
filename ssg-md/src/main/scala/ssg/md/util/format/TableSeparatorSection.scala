/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableSeparatorSection.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package format

class TableSeparatorSection(sectionType: TableSectionType) extends TableSection(sectionType) {

  override def defaultRow(): TableRow =
    new TableSeparatorRow()

  override def defaultCell(): TableCell =
    TableSeparatorSection.DEFAULT_CELL
}

object TableSeparatorSection {
  val DEFAULT_CELL: TableCell = new TableCell("---", 1, 1)
}
