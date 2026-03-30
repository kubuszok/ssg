/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-escaped-character/src/main/java/com/vladsch/flexmark/ext/escaped/character/EscapedCharacter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package escaped
package character

import ssg.md.util.ast.{DoNotDecorate, Node}
import ssg.md.util.sequence.BasedSequence

/** A EscapedCharacter node */
class EscapedCharacter() extends Node with DoNotDecorate {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var text: BasedSequence = BasedSequence.NULL

  override def segments: Array[BasedSequence] = Array(openingMarker, text)

  override def astExtra(out: StringBuilder): Unit = {
    Node.delimitedSegmentSpanChars(out, openingMarker, text, BasedSequence.NULL, "text")
  }

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(openingMarker: BasedSequence, text: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, text.endOffset)
    this.openingMarker = openingMarker
    this.text = text
  }
}
