/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableBody.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

/** Body part of a [[TableBlock]] containing [[TableRow]]s. */
class TableBody() extends Node {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS
}
