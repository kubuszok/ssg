/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/WhiteSpace.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/WhiteSpace.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

/** Only generated for CharacterNodeFactory custom parsing
  */
class WhiteSpace extends Node {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def astExtra(out: StringBuilder): Unit =
    astExtraChars(out)

  override protected def toStringAttributes: String =
    "text=" + chars
}
