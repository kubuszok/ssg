/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/GitLabBlockQuote.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gitlab

import ssg.md.ast.{Paragraph, ParagraphContainer}
import ssg.md.util.ast.{Block, BlockContent, Node}
import ssg.md.util.sequence.BasedSequence

import java.{util => ju}
import scala.language.implicitConversions

/** A GitLab block quote node */
class GitLabBlockQuote() extends Block, ParagraphContainer {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var openingTrailing: BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL
  var closingTrailing: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, segments: ju.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.contentLines = segments
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpanChars(out, openingMarker, "open")
    Node.segmentSpanChars(out, openingTrailing, "openTrail")
    Node.segmentSpanChars(out, closingMarker, "close")
    Node.segmentSpanChars(out, closingTrailing, "closeTrail")
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, openingTrailing, closingMarker, closingTrailing)

  override def isParagraphEndWrappingDisabled(node: Paragraph): Boolean = {
    lastChild.contains(node) || node.next.exists(_.isInstanceOf[GitLabBlockQuote])
  }

  override def isParagraphStartWrappingDisabled(node: Paragraph): Boolean = {
    firstChild.contains(node) || node.previous.exists(_.isInstanceOf[GitLabBlockQuote])
  }
}
