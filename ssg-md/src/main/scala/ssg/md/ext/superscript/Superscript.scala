/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-superscript/src/main/java/com/vladsch/flexmark/ext/superscript/Superscript.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-superscript/src/main/java/com/vladsch/flexmark/ext/superscript/Superscript.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package superscript

import ssg.md.util.ast.{ DelimitedNode, Node }
import ssg.md.util.sequence.BasedSequence

/** A Superscript node */
class Superscript() extends Node, DelimitedNode {

  var openingMarker:        BasedSequence = BasedSequence.NULL
  var text:                 BasedSequence = BasedSequence.NULL
  var closingMarker:        BasedSequence = BasedSequence.NULL
  var superscriptBlockText: String        = scala.compiletime.uninitialized

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit =
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")

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

  def this(chars: BasedSequence, superscriptBlockText: String) = {
    this()
    this.chars = chars
    this.superscriptBlockText = superscriptBlockText
  }
}
