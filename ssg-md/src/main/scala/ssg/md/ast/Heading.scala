/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/Heading.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.Block
import ssg.md.util.ast.BlockContent
import ssg.md.util.ast.Node
import ssg.md.util.ast.TextCollectingVisitor
import ssg.md.util.ast.TextContainer
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }

class Heading extends Block, AnchorRefTarget {

  var level:                        Int           = 0
  var openingMarker:                BasedSequence = BasedSequence.NULL
  var text:                         BasedSequence = BasedSequence.NULL
  var closingMarker:                BasedSequence = BasedSequence.NULL
  private var _anchorRefId:         String        = ""
  private var _explicitAnchorRefId: Boolean       = false

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, segments: ju.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.lineSegments = segments
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  override def astExtra(out: StringBuilder): Unit =
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")

  override def segments: Array[BasedSequence] =
    Array(openingMarker, text, closingMarker)

  override def anchorRefText: String = {
    // NOTE: HtmlRenderer.HEADER_ID_REF_TEXT_TRIM_LEADING_SPACES / TRAILING_SPACES
    // are not yet ported; using defaults (trim both) for now
    val trimLeadingSpaces  = true
    val trimTrailingSpaces = true

    new TextCollectingVisitor().collectAndGetText(
      this,
      TextContainer.F_FOR_HEADING_ID +
        (if (trimLeadingSpaces) 0 else TextContainer.F_NO_TRIM_REF_TEXT_START) +
        (if (trimTrailingSpaces) 0 else TextContainer.F_NO_TRIM_REF_TEXT_END)
    )
  }

  override def anchorRefId: String = _anchorRefId

  override def anchorRefId_=(anchorRefId: String): Unit =
    _anchorRefId = anchorRefId

  override def isExplicitAnchorRefId: Boolean = _explicitAnchorRefId

  override def explicitAnchorRefId_=(value: Boolean): Unit =
    _explicitAnchorRefId = value

  def isAtxHeading: Boolean = openingMarker ne BasedSequence.NULL

  def isSetextHeading: Boolean = (openingMarker eq BasedSequence.NULL) && (closingMarker ne BasedSequence.NULL)
}
