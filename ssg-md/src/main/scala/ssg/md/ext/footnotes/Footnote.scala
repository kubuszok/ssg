/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/Footnote.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes

import ssg.md.Nullable
import ssg.md.ast.LinkRendered
import ssg.md.ext.footnotes.internal.FootnoteRepository
import ssg.md.util.ast.{DelimitedNode, DoNotDecorate, Document, Node, ReferencingNode}
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

/**
 * A Footnote referencing node
 */
class Footnote() extends Node with DelimitedNode with DoNotDecorate with LinkRendered with ReferencingNode[FootnoteRepository, FootnoteBlock] {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var text: BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL
  var footnoteBlock: Nullable[FootnoteBlock] = Nullable.empty
  var referenceOrdinal: Int = 0

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

  override def reference: BasedSequence = text

  override def getReferenceNode(document: Document): FootnoteBlock = {
    if (footnoteBlock.isDefined || text.isEmpty) footnoteBlock.getOrElse(null.asInstanceOf[FootnoteBlock]) // @nowarn - Java interop: ReferencingNode returns nullable
    else {
      footnoteBlock = Nullable(getFootnoteBlock(FootnoteExtension.FOOTNOTES.get(document)))
      footnoteBlock.getOrElse(null.asInstanceOf[FootnoteBlock]) // @nowarn - Java interop: ReferencingNode returns nullable
    }
  }

  override def getReferenceNode(repository: FootnoteRepository): FootnoteBlock = {
    if (footnoteBlock.isDefined || text.isEmpty) footnoteBlock.getOrElse(null.asInstanceOf[FootnoteBlock]) // @nowarn - Java interop: ReferencingNode returns nullable
    else {
      footnoteBlock = Nullable(getFootnoteBlock(repository))
      footnoteBlock.getOrElse(null.asInstanceOf[FootnoteBlock]) // @nowarn - Java interop: ReferencingNode returns nullable
    }
  }

  override def isDefined: Boolean = footnoteBlock.isDefined

  /** @return true if this node will be rendered as text because it depends on a reference which is not defined. */
  override def isTentative: Boolean = footnoteBlock.isEmpty

  def getFootnoteBlock(footnoteRepository: FootnoteRepository): FootnoteBlock = {
    if (text.isEmpty) null.asInstanceOf[FootnoteBlock] // @nowarn - Java interop: may return null
    else footnoteRepository.get(text.toString)
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit = {
    out.append(" ordinal: ").append(footnoteBlock.map(_.footnoteOrdinal).getOrElse(0)).append(" ")
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")
  }
}
