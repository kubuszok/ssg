/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/AsideBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/AsideBlock.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package aside

import ssg.md.util.ast.{ Block, BlockContent, BlockQuoteLike, KeepTrailingBlankLineContainer, Node }
import ssg.md.util.sequence.BasedSequence

/** A ExtAside block node */
class AsideBlock() extends Block, BlockQuoteLike, KeepTrailingBlankLineContainer {

  var openingMarker: BasedSequence = BasedSequence.NULL

  override def astExtra(out: StringBuilder): Unit =
    Node.segmentSpanChars(out, openingMarker, "marker")

  override def segments: Array[BasedSequence] = Array(openingMarker)

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, segments: java.util.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.contentLines = segments
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }
}
