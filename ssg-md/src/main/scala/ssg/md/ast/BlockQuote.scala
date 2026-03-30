/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/BlockQuote.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.Block
import ssg.md.util.ast.BlockContent
import ssg.md.util.ast.BlockQuoteLike
import ssg.md.util.ast.KeepTrailingBlankLineContainer
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }

class BlockQuote extends Block with BlockQuoteLike with KeepTrailingBlankLineContainer {

  var openingMarker: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, segments: ju.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.lineSegments = segments
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  override def astExtra(out: StringBuilder): Unit =
    Node.segmentSpanChars(out, openingMarker, "marker")

  override def segments: Array[BasedSequence] =
    Array(openingMarker)
}
