/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/OrderedList.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/OrderedList.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast

import ssg.md.util.ast.BlockContent
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }

class OrderedList extends ListBlock {

  var startNumber: Int  = 0
  var delimiter:   Char = scala.compiletime.uninitialized

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

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def astExtra(out: StringBuilder): Unit = {
    super.astExtra(out)
    if (startNumber > 1) out.append(" start:").append(startNumber)
    out.append(" delimiter:'").append(delimiter).append("'")
  }
}
