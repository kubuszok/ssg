/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/AutoLink.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/AutoLink.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

class AutoLink extends DelimitedLinkNode {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(openingMarker: BasedSequence, text: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
    this.openingMarker = openingMarker
    this.text = text
    this.closingMarker = closingMarker
    setUrlChars(text)
  }

  override def segments: Array[BasedSequence] =
    Array(openingMarker, pageRef, anchorMarker, anchorRef, closingMarker)

  override def segmentsForChars: Array[BasedSequence] =
    Array(openingMarker, pageRef, anchorMarker, anchorRef, closingMarker)

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpanChars(out, openingMarker, "open")
    Node.segmentSpanChars(out, text, "text")
    if (pageRef.isNotNull) Node.segmentSpanChars(out, pageRef, "pageRef")
    if (anchorMarker.isNotNull) Node.segmentSpanChars(out, anchorMarker, "anchorMarker")
    if (anchorRef.isNotNull) Node.segmentSpanChars(out, anchorRef, "anchorRef")
    Node.segmentSpanChars(out, closingMarker, "close")
  }
}
