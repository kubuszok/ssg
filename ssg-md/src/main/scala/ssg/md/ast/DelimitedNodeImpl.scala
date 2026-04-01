/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/DelimitedNodeImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.DelimitedNode
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

abstract class DelimitedNodeImpl extends Node, DelimitedNode {
  var openingMarker: BasedSequence = BasedSequence.NULL
  var text:          BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL

  override def astExtra(out: StringBuilder): Unit =
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")

  override def segments: Array[BasedSequence] =
    Array(openingMarker, text, closingMarker)

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(openingMarker: BasedSequence, text: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
    this.openingMarker = openingMarker
    this.text = text
    this.closingMarker = closingMarker
  }
}
