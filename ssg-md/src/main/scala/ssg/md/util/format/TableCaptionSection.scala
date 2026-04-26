/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCaptionSection.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCaptionSection.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format

import ssg.md.util.sequence.BasedSequence

class TableCaptionSection(sectionType: TableSectionType) extends TableSection(sectionType) {

  override def defaultRow(): TableRow =
    new TableCaptionRow()

  override def defaultCell(): TableCell =
    TableCaptionSection.DEFAULT_CELL
}

object TableCaptionSection {
  val NULL_CELL:    TableCell = new TableCell(Nullable(null), BasedSequence.NULL, BasedSequence.NULL, BasedSequence.NULL, 1, 0)
  val DEFAULT_CELL: TableCell = new TableCell(Nullable(null), "[", "", "]", 1, 1)
}
