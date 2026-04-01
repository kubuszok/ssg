/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/Code.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.DoNotLinkDecorate
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.TextContainer
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.ISequenceBuilder

class Code extends DelimitedNodeImpl, DoNotLinkDecorate, TextContainer {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(openingMarker: BasedSequence, content: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
    this.openingMarker = openingMarker
    this.text = content
    this.closingMarker = closingMarker
  }

  override def astExtra(out: StringBuilder): Unit =
    Node.delimitedSegmentSpan(out, openingMarker, text, closingMarker, "text")

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean = {
    out.append(text)
    false
  }
}
