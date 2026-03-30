/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlankLine.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.sequence.BasedSequence

class BlankLine(chars: BasedSequence) extends Block(chars) {

  private var _claimedBlankLine: Nullable[Block] = Nullable.empty

  setCharsFromContent()

  def this(chars: BasedSequence, claimedBlankLine: Block) = {
    this(chars)
    _claimedBlankLine = Nullable(claimedBlankLine)
  }

  def isClaimed: Boolean = _claimedBlankLine.isDefined

  def claimedBlankLine: Nullable[Block] = _claimedBlankLine

  def claimedBlankLine_=(claimedBlankLine: Block): BlankLine = {
    _claimedBlankLine = Nullable(claimedBlankLine)
    this
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS
}
