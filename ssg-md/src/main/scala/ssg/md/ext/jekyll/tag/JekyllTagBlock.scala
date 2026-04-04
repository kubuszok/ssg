/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/JekyllTagBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package jekyll
package tag

import ssg.md.util.ast.{ Block, BlockContent, Node }
import ssg.md.util.sequence.BasedSequence

import java.util.List as JList

/** A JekyllTag block node */
class JekyllTagBlock() extends Block {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, lineSegments: JList[BasedSequence]) = {
    this()
    this.chars = chars
    this.contentLines = lineSegments
  }

  def this(lineSegments: JList[BasedSequence]) = {
    this()
    this.contentLines = lineSegments
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  def this(node: Node) = {
    this()
    appendChild(node)
    setCharsFromContent()
  }

  override def astExtra(out: StringBuilder): Unit = {}

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS
}
