/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/HardLineBreak.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.DoNotTrim
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.TextContainer
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.ISequenceBuilder

import scala.language.implicitConversions

class HardLineBreak extends Node, DoNotTrim, TextContainer {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean = {
    val ch = chars
    out.add(ch.subSequence(ch.length - 1, ch.length))
    false
  }
}
