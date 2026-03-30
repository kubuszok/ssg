/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/AttributesNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes

import ssg.md.util.ast.{DelimitedNode, DoNotDecorate, Node, NonRenderingInline}
import ssg.md.util.sequence.BasedSequence

/** A AttributesNode node */
class AttributesNode() extends Node with DelimitedNode with DoNotDecorate with NonRenderingInline {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var text: BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = { this(); this.chars = chars }

  def this(openingMarker: BasedSequence, text: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
    this.openingMarker = openingMarker
    this.text = text
    this.closingMarker = closingMarker
  }

  def this(chars: BasedSequence, attributesBlockText: String) = { this(); this.chars = chars }

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit = {
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")
  }
}
