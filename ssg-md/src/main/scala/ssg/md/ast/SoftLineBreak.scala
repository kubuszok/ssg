/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/SoftLineBreak.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.DoNotAttributeDecorate
import ssg.md.util.ast.DoNotTrim
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.TextContainer
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.ISequenceBuilder

import scala.language.implicitConversions

class SoftLineBreak extends Node with DoNotAttributeDecorate with DoNotTrim with TextContainer {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def chars_=(chars: BasedSequence): Unit = {
    super.chars_=(chars)
    assert(this.chars.isNotEmpty())
  }

  override def setCharsFromContentOnly(): Unit = {
    super.setCharsFromContentOnly()
    assert(chars.isNotEmpty())
  }

  override def setCharsFromContent(): Unit = {
    super.setCharsFromContent()
    assert(chars.isNotEmpty())
  }

  override def setCharsFromSegments(): Unit = {
    super.setCharsFromSegments()
    assert(chars.isNotEmpty())
  }

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean = {
    out.add(chars)
    false
  }
}
