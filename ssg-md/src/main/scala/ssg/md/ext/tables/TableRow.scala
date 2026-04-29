/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableRow.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableRow.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package tables

import ssg.md.util.ast.{ LineBreakNode, Node }
import ssg.md.util.sequence.BasedSequence

/** Table row of a [[TableHead]] or [[TableBody]] containing [[TableCell]]s. */
class TableRow() extends Node, LineBreakNode {

  /** rowNumber within the table section: header, body, separator */
  var rowNumber: Int = 0

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def astExtra(out: StringBuilder): Unit =
    // Node.astExtra is empty, no need for super call
    if (rowNumber != 0) out.append(" rowNumber=").append(rowNumber)

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS
}
