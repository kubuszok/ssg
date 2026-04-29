/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/Reference.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/Reference.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast

import ssg.md.Nullable
import ssg.md.ast.util.ReferenceRepository
import ssg.md.util.ast.Node
import ssg.md.util.ast.ReferenceNode
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.PrefixedSubSequence
import ssg.md.util.sequence.SequenceUtils

import scala.language.implicitConversions

class Reference(label: BasedSequence, urlSeq: BasedSequence, titleSeq: Nullable[BasedSequence]) extends LinkNodeBase(BasedSequence.NULL), ReferenceNode[ReferenceRepository, Reference, RefNode] {

  var openingMarker: BasedSequence = label.subSequence(0, 1)
  var reference:     BasedSequence = label.subSequence(1, label.length - 2).trim()
  var closingMarker: BasedSequence = label.subSequence(label.length - 2, label.length)

  setUrlChars(urlSeq)

  if (titleSeq.isDefined && (titleSeq.get ne BasedSequence.NULL)) {
    titleOpeningMarker = titleSeq.get.subSequence(0, 1)
    title = titleSeq.get.subSequence(1, titleSeq.get.length - 1)
    titleClosingMarker = titleSeq.get.subSequence(titleSeq.get.length - 1, titleSeq.get.length)
  }
  setCharsFromContent()

  override def segments: Array[BasedSequence] =
    Array(
      openingMarker,
      reference,
      closingMarker,
      urlOpeningMarker,
      url,
      pageRef,
      anchorMarker,
      anchorRef,
      urlClosingMarker,
      titleOpeningMarker,
      title,
      titleClosingMarker
    )

  override def segmentsForChars: Array[BasedSequence] =
    Array(
      openingMarker,
      reference,
      closingMarker,
      PrefixedSubSequence.prefixOf(" ", closingMarker.getEmptySuffix),
      urlOpeningMarker,
      pageRef,
      anchorMarker,
      anchorRef,
      urlClosingMarker,
      titleOpeningMarker,
      title,
      titleClosingMarker
    )

  override def compareTo(other: Reference): Int =
    SequenceUtils.compare(reference, other.reference, true)

  override def referencingNode(node: Node): Nullable[RefNode] =
    node match {
      case refNode: RefNode => Nullable(refNode)
      case _ => Nullable.empty
    }

  override def astExtra(out: StringBuilder): Unit = {
    Node.delimitedSegmentSpanChars(out, openingMarker, reference, closingMarker, "ref")
    Node.delimitedSegmentSpanChars(out, urlOpeningMarker, url, urlClosingMarker, "url")
    Node.delimitedSegmentSpanChars(out, titleOpeningMarker, title, titleClosingMarker, "title")
  }

  override protected def toStringAttributes: String =
    "reference=" + reference + ", url=" + url
}
