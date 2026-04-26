/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/Emoji.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/Emoji.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package emoji

import ssg.md.html.HtmlRenderer
import ssg.md.util.ast.{ DelimitedNode, Node, NodeVisitor, TextContainer }
import ssg.md.util.misc.BitFieldSet
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.ISequenceBuilder

import scala.language.implicitConversions

/** An emoji node containing emoji shortcut text */
class Emoji() extends Node, DelimitedNode, TextContainer {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var text:          BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL

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

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit =
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean = {
    if (BitFieldSet.any(flags, TextContainer.F_FOR_HEADING_ID)) {
      if (HtmlRenderer.HEADER_ID_ADD_EMOJI_SHORTCUT.get(document)) {
        out.append(text)
      }
    }
    false
  }
}
