/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/FootnoteBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes

import ssg.md.Nullable
import ssg.md.ast.{ Paragraph, ParagraphItemContainer }
import ssg.md.ext.footnotes.internal.FootnoteRepository
import ssg.md.util.ast.{ Block, Node, ReferenceNode }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }

import scala.language.implicitConversions

/** A Footnote definition node containing text and other inline nodes nodes as children.
  */
class FootnoteBlock() extends Block, ReferenceNode[FootnoteRepository, FootnoteBlock, Footnote], ParagraphItemContainer {

  var openingMarker:        BasedSequence = BasedSequence.NULL
  var text:                 BasedSequence = BasedSequence.NULL
  var closingMarker:        BasedSequence = BasedSequence.NULL
  var footnote:             BasedSequence = BasedSequence.NULL
  var footnoteOrdinal:      Int           = 0
  var firstReferenceOffset: Int           = Int.MaxValue
  var footnoteReferences:   Int           = 0

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def compareTo(other: FootnoteBlock): Int =
    SequenceUtils.compare(text, other.text, true)

  override def referencingNode(node: Node): Nullable[Footnote] =
    node match {
      case fn: Footnote => Nullable(fn)
      case _ => Nullable.empty
    }

  def addFirstReferenceOffset(offset: Int): Unit =
    if (this.firstReferenceOffset < offset) this.firstReferenceOffset = offset

  def isReferenced: Boolean = this.firstReferenceOffset < Int.MaxValue

  override def astExtra(out: StringBuilder): Unit = {
    out.append(" ordinal: ").append(footnoteOrdinal).append(" ")
    Node.segmentSpan(out, openingMarker, "open")
    Node.segmentSpan(out, text, "text")
    Node.segmentSpan(out, closingMarker, "close")
    Node.segmentSpan(out, footnote, "footnote")
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker, footnote)

  override def isItemParagraph(node: Paragraph): Boolean =
    firstChild.contains(node)

  override def isParagraphWrappingDisabled(node: Paragraph, listOptions: Any, options: DataHolder): Boolean = false

  override def isParagraphInTightListItem(node: Paragraph): Boolean = false
}
