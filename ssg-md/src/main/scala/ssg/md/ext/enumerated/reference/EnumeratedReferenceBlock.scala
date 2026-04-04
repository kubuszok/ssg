/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferenceBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference

import ssg.md.Nullable
import ssg.md.ast.{ Paragraph, ParagraphItemContainer }
import ssg.md.util.ast.{ Block, Node, ReferenceNode }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }

import scala.language.implicitConversions

/** A EnumeratedReference block node */
class EnumeratedReferenceBlock() extends Block, ReferenceNode[EnumeratedReferenceRepository, EnumeratedReferenceBlock, EnumeratedReferenceText], ParagraphItemContainer {

  var openingMarker:       BasedSequence = BasedSequence.NULL
  var text:                BasedSequence = BasedSequence.NULL
  var closingMarker:       BasedSequence = BasedSequence.NULL
  var enumeratedReference: BasedSequence = BasedSequence.NULL

  override def compareTo(other: EnumeratedReferenceBlock): Int =
    SequenceUtils.compare(text, other.text, true)

  override def referencingNode(node: Node): Nullable[EnumeratedReferenceText] =
    node match {
      case ref: EnumeratedReferenceText => Nullable(ref)
      case _ => Nullable.empty
    }

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpan(out, openingMarker, "open")
    Node.segmentSpan(out, text, "text")
    Node.segmentSpan(out, closingMarker, "close")
    Node.segmentSpan(out, enumeratedReference, "enumeratedReference")
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker, enumeratedReference)

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def isItemParagraph(node: Paragraph): Boolean =
    firstChild.contains(node)

  override def isParagraphWrappingDisabled(node: Paragraph, listOptions: Any, options: DataHolder): Boolean = true

  override def isParagraphInTightListItem(node: Paragraph): Boolean = true
}
