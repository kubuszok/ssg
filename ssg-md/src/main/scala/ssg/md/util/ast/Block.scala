/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/Block.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/Block.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }

abstract class Block extends ContentNode {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, lineSegments: ju.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.lineSegments = lineSegments
  }

  def this(lineSegments: ju.List[BasedSequence]) = {
    this()
    setContent(lineSegments)
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  def parentBlock: Nullable[Block] =
    parent.map(_.asInstanceOf[Block])

  override protected def parent_=(parent: Nullable[Node]): Unit = {
    if (parent.isDefined && !parent.get.isInstanceOf[Block]) {
      throw new IllegalArgumentException("Parent of block must also be block (can not be inline)")
    }
    super.parent_=(parent)
  }
}
