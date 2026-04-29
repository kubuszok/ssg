/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocContent.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocContent.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package toc

import ssg.md.util.ast.{ Block, BlockContent, DoNotDecorate, Node }
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }

/** A sim toc contents node containing all text that came after the sim toc node */
class SimTocContent() extends Block, DoNotDecorate {

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def astExtra(out: StringBuilder): Unit = {}

  def this(chars: BasedSequence) = { this(); this.chars = chars }

  def this(chars: BasedSequence, lineSegments: ju.List[BasedSequence]) = { this(); this.chars = chars; this.contentLines = lineSegments }

  def this(lineSegments: ju.List[BasedSequence]) = { this(); this.contentLines = lineSegments }

  def this(blockContent: BlockContent) = { this(); setContent(blockContent) }
}
