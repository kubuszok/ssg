/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableRowManipulator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package format

import java.util.ArrayList

/** manipulate rows in a table
  */
trait TableRowManipulator {

  /** @param row
    *   row for the operation
    * @param allRowsIndex
    *   row's index in all rows of the request reflects indices at time of request, when rows are deleted those rows will not be processed and their indices will skipped
    * @param sectionRows
    *   rows for the section of the row
    * @param sectionRowIndex
    *   index for the row in the section's rows
    * @return
    *   action performed: &lt;0 number of rows deleted, 0 - no change to rows, &gt;0 - number of rows added, or BREAK to stop processing rows
    */
  def apply(row: TableRow, allRowsIndex: Int, sectionRows: ArrayList[TableRow], sectionRowIndex: Int): Int
}

object TableRowManipulator {
  val BREAK: Int = Integer.MIN_VALUE
}
