/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/TypographicQuotes.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/TypographicQuotes.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package typographic

import ssg.md.Nullable
import ssg.md.util.ast.{ DelimitedNode, DoNotAttributeDecorate, Node, TypographicText }
import ssg.md.util.sequence.BasedSequence

/** A TypographicQuotes node */
class TypographicQuotes() extends Node, DelimitedNode, DoNotAttributeDecorate, TypographicText {

  var openingMarker:      BasedSequence    = BasedSequence.NULL
  var text:               BasedSequence    = BasedSequence.NULL
  var closingMarker:      BasedSequence    = BasedSequence.NULL
  var typographicOpening: Nullable[String] = Nullable.empty
  var typographicClosing: Nullable[String] = Nullable.empty

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
  }

  override def segments: Array[BasedSequence] =
    // return EMPTY_SEGMENTS;
    Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit = {
    if (openingMarker.isNotNull) out.append(" typographicOpening: ").append(typographicOpening.getOrElse("null")).append(" ")
    if (closingMarker.isNotNull) out.append(" typographicClosing: ").append(typographicClosing.getOrElse("null")).append(" ")
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")
  }
}
