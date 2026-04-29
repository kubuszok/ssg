/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCellConsumer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCellConsumer.java
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
trait TableCellConsumer {

  /** @param cell
    *   cell
    * @param cellIndex
    *   cell's index in row cells
    * @param cellColumn
    *   cell's starting column (if previous cells had column spans not same as cell index)
    */
  def accept(cell: TableCell, cellIndex: Int, cellColumn: Int): Unit
}
