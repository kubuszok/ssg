/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/FencedCodeBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.Block
import ssg.md.util.ast.DoNotDecorate
import ssg.md.util.ast.Node
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }

import scala.language.implicitConversions

class FencedCodeBlock extends Block with DoNotDecorate {

  var fenceIndent:   Int           = 0
  var openingMarker: BasedSequence = BasedSequence.NULL
  var info:          BasedSequence = BasedSequence.NULL
  var attributes:    BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, openingMarker: BasedSequence, info: BasedSequence, segments: ju.List[BasedSequence], closingMarker: BasedSequence) = {
    this()
    this.chars = chars
    this.lineSegments = segments
    this.openingMarker = openingMarker
    this.info = info
    this.closingMarker = closingMarker
  }

  override def astExtra(out: StringBuilder): Unit = {
    val content = contentChars
    val lines   = contentLines.size
    Node.segmentSpanChars(out, openingMarker, "open")
    Node.segmentSpanChars(out, info, "info")
    Node.segmentSpanChars(out, attributes, "attributes")
    Node.segmentSpan(out, content, "content")
    out.append(" lines[").append(lines).append("]")
    Node.segmentSpanChars(out, closingMarker, "close")
  }

  override def segments: Array[BasedSequence] =
    Array(openingMarker, info, attributes, contentChars, closingMarker)

  def openingFence: BasedSequence = openingMarker

  /** @return
    *   the sequence for the info part of the node
    * @see
    *   <a href="http://spec.commonmark.org/0.18/#info-string">CommonMark spec</a>
    */
  def infoDelimitedByAny(delimiters: CharPredicate): BasedSequence = {
    var language: BasedSequence = BasedSequence.NULL
    if (info.isNotNull && !info.isBlank()) {
      val delimiter = info.indexOfAny(delimiters)
      if (delimiter == -1) {
        language = info
      } else {
        language = info.subSequence(0, delimiter)
      }
    }
    language
  }

  def closingFence: BasedSequence = closingMarker

  def fenceLength: Int = info.length
}
