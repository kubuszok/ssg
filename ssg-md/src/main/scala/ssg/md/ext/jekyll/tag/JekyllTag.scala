/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/JekyllTag.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/JekyllTag.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package jekyll
package tag

import ssg.md.util.ast.{ Block, Node }
import ssg.md.util.sequence.BasedSequence

/** A JekyllTag node */
class JekyllTag() extends Block {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var tag:           BasedSequence = BasedSequence.NULL
  var parameters:    BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(openingMarker: BasedSequence, tag: BasedSequence, parameters: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
    this.openingMarker = openingMarker
    this.tag = tag
    this.parameters = parameters
    this.closingMarker = closingMarker
  }

  override def segments: Array[BasedSequence] =
    // return EMPTY_SEGMENTS;
    Array(openingMarker, tag, parameters, closingMarker)

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpanChars(out, openingMarker, "open")
    Node.segmentSpanChars(out, tag, "tag")
    Node.segmentSpanChars(out, parameters, "parameters")
    Node.segmentSpanChars(out, closingMarker, "close")
  }
}
