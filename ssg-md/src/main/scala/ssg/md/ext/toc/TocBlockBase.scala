/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/TocBlockBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/TocBlockBase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package toc

import ssg.md.util.ast.{ Block, Node }
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

/** A TOC node base class */
abstract class TocBlockBase(chars: BasedSequence, styleChars: BasedSequence, closingSimToc: Boolean) extends Block(chars) {

  var openingMarker: BasedSequence = chars.subSequence(0, 1)
  var tocKeyword:    BasedSequence = chars.subSequence(1, 4)
  var style:         BasedSequence = if (styleChars != null) styleChars else BasedSequence.NULL // @nowarn - Java interop: may be passed null
  var closingMarker: BasedSequence = {
    val closingPos = chars.indexOf(']', 4)
    if (closingSimToc && !(closingPos != -1 && closingPos + 1 < chars.length() && chars.charAt(closingPos + 1) == ':')) {
      throw new IllegalStateException("Invalid TOC block sequence")
    }
    chars.subSequence(closingPos, closingPos + (if (closingSimToc) 2 else 1))
  }

  def this(chars: BasedSequence) = this(chars, null.asInstanceOf[BasedSequence], false) // @nowarn - overloaded ctor
  def this(chars: BasedSequence, closingSimToc: Boolean) = this(chars, null.asInstanceOf[BasedSequence], closingSimToc) // @nowarn - overloaded ctor
  def this(chars: BasedSequence, styleChars:    BasedSequence) = this(chars, styleChars, false)

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpan(out, openingMarker, "openingMarker")
    Node.segmentSpan(out, tocKeyword, "tocKeyword")
    Node.segmentSpan(out, style, "style")
    Node.segmentSpan(out, closingMarker, "closingMarker")
  }

  override def segments: Array[BasedSequence] = {
    val nodeSegments = Array(openingMarker, tocKeyword, style, closingMarker)
    if (lineSegments.size() == 0) nodeSegments
    else {
      val allSegments = new Array[BasedSequence](lineSegments.size() + nodeSegments.length)
      lineSegments.toArray(allSegments)
      System.arraycopy(allSegments, 0, allSegments, nodeSegments.length, lineSegments.size())
      allSegments
    }
  }
}
