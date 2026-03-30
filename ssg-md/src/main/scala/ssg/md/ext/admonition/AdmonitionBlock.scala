/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/AdmonitionBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package admonition

import ssg.md.ast.{Paragraph, ParagraphContainer}
import ssg.md.util.ast.{Block, Node}
import ssg.md.util.sequence.BasedSequence

import java.{util => ju}
import scala.language.implicitConversions

/**
 * An Admonition block node
 */
class AdmonitionBlock() extends Block with ParagraphContainer {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var info: BasedSequence = BasedSequence.NULL
  var titleOpeningMarker: BasedSequence = BasedSequence.NULL
  var title: BasedSequence = BasedSequence.NULL
  var titleClosingMarker: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, openingMarker: BasedSequence, info: BasedSequence, segments: ju.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.contentLines = segments
    this.openingMarker = openingMarker
    this.info = info
  }

  override def segments: Array[BasedSequence] = {
    Array(openingMarker, info, titleOpeningMarker, title, titleClosingMarker)
  }

  override def segmentsForChars: Array[BasedSequence] = {
    Array(openingMarker, info, titleOpeningMarker, title, titleClosingMarker)
  }

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpanChars(out, openingMarker, "open")
    Node.segmentSpanChars(out, info, "info")
    Node.delimitedSegmentSpanChars(out, titleOpeningMarker, title, titleClosingMarker, "title")
  }

  def titleChars: BasedSequence = Node.spanningChars(titleOpeningMarker, title, titleClosingMarker)

  def titleChars_=(titleChars: BasedSequence): Unit = {
    if (titleChars != null && (titleChars ne BasedSequence.NULL)) { // @nowarn - Java interop: defensive null check
      val titleCharsLength = titleChars.length()
      titleOpeningMarker = titleChars.subSequence(0, 1)
      title = titleChars.subSequence(1, titleCharsLength - 1)
      titleClosingMarker = titleChars.subSequence(titleCharsLength - 1, titleCharsLength)
    } else {
      titleOpeningMarker = BasedSequence.NULL
      title = BasedSequence.NULL
      titleClosingMarker = BasedSequence.NULL
    }
  }

  override def isParagraphEndWrappingDisabled(node: Paragraph): Boolean = false

  override def isParagraphStartWrappingDisabled(node: Paragraph): Boolean = {
    if (firstChild.contains(node)) {
      // need to see if there is a blank line between it and our start
      val ourEOL = chars.getBaseSequence.endOfLine(chars.startOffset)
      val childStartEOL = node.startOfLine
      ourEOL + 1 == childStartEOL
    } else {
      false
    }
  }
}
