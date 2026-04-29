/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocBlock.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package toc

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

/** A simulated toc block node */
class SimTocBlock(chars: BasedSequence, styleChars: BasedSequence, titleChars: BasedSequence) extends TocBlockBase(chars, styleChars, true) {

  var anchorMarker: BasedSequence = {
    val anchorPos = chars.indexOf('#', closingMarker.endOffset - chars.startOffset)
    if (anchorPos == -1) throw new IllegalStateException("Invalid TOC block sequence")
    chars.subSequence(anchorPos, anchorPos + 1)
  }

  var openingTitleMarker: BasedSequence = BasedSequence.NULL
  var title:              BasedSequence = BasedSequence.NULL
  var closingTitleMarker: BasedSequence = BasedSequence.NULL

  if (titleChars != null) { // @nowarn - Java interop: may be null
    if (titleChars.length() < 2) throw new IllegalStateException("Invalid TOC block title sequence")
    openingTitleMarker = titleChars.subSequence(0, 1)
    title = titleChars.midSequence(1, -1)
    closingTitleMarker = titleChars.endSequence(1)
  }

  def this(chars: BasedSequence) = this(chars, null.asInstanceOf[BasedSequence], null.asInstanceOf[BasedSequence]) // @nowarn - overloaded ctor

  override def astExtra(out: StringBuilder): Unit = {
    super.astExtra(out)
    Node.segmentSpanChars(out, anchorMarker, "anchorMarker")
    Node.segmentSpanChars(out, openingTitleMarker, "openingTitleMarker")
    Node.segmentSpanChars(out, title, "title")
    Node.segmentSpanChars(out, closingTitleMarker, "closingTitleMarker")
  }

  override def segments: Array[BasedSequence] = {
    val nodeSegments = Array(openingMarker, tocKeyword, style, closingMarker, anchorMarker, openingTitleMarker, title, closingTitleMarker)
    if (lineSegments.size() == 0) nodeSegments
    else {
      val allSegments = new Array[BasedSequence](lineSegments.size() + nodeSegments.length)
      lineSegments.toArray(allSegments)
      System.arraycopy(allSegments, 0, allSegments, nodeSegments.length, lineSegments.size())
      allSegments
    }
  }
}
