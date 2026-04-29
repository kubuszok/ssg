/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCellManipulator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCellManipulator.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

/** loop over table row cells
  */
trait TableCellManipulator {

  /** @param cell
    *   cell
    * @param cellIndex
    *   cell's index in row cells
    * @param cellColumn
    *   cell's starting column (if previous cells had column spans not same as cell index)
    * @param allCellIndex
    *   cell's index as would be at the beginning of the request, deleted cell indices skipped
    * @return
    *   change to cells &lt;0 of cells deleted, &gt;0 number inserted, 0 no change, or BREAK to stop processing cells
    */
  def apply(cell: TableCell, cellIndex: Int, cellColumn: Int, allCellIndex: Int): Int
}

object TableCellManipulator {
  val BREAK: Int = Integer.MIN_VALUE
}
