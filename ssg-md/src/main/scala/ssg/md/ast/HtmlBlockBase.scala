/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/HtmlBlockBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/HtmlBlockBase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast

import ssg.md.util.ast.Block
import ssg.md.util.ast.BlockContent
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }

/** HTML block
  *
  * @see
  *   <a href="http://spec.commonmark.org/0.18/#html-blocks">CommonMark Spec</a>
  */
abstract class HtmlBlockBase extends Block {

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
}
