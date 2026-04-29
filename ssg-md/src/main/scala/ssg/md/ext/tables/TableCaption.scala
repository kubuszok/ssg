/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableCaption.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableCaption.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package tables

import ssg.md.util.ast.{ DelimitedNode, LineBreakNode, Node }
import ssg.md.util.sequence.BasedSequence

/** Table caption of a [[TableBlock]] containing inline nodes. */
class TableCaption(
  var openingMarker: BasedSequence,
  var text:          BasedSequence,
  var closingMarker: BasedSequence
) extends Node,
      DelimitedNode,
      LineBreakNode {

  def this(chars: BasedSequence, openingMarker: BasedSequence, text: BasedSequence, closingMarker: BasedSequence) = {
    this(openingMarker, text, closingMarker)
    this.chars = chars
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit =
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")
}
