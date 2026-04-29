/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/RefNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/RefNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast

import ssg.md.ast.util.ReferenceRepository
import ssg.md.util.ast.DoNotLinkDecorate
import ssg.md.util.ast.Document
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.ReferencingNode
import ssg.md.util.ast.TextContainer
import ssg.md.util.misc.BitFieldSet
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.Escaping
import ssg.md.util.sequence.ReplacedTextMapper
import ssg.md.util.sequence.builder.ISequenceBuilder

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

abstract class RefNode extends Node, LinkRefDerived, ReferencingNode[ReferenceRepository, Reference], DoNotLinkDecorate, TextContainer {
  var textOpeningMarker:      BasedSequence = BasedSequence.NULL
  var text:                   BasedSequence = BasedSequence.NULL
  var textClosingMarker:      BasedSequence = BasedSequence.NULL
  var referenceOpeningMarker: BasedSequence = BasedSequence.NULL
  var reference:              BasedSequence = BasedSequence.NULL
  var referenceClosingMarker: BasedSequence = BasedSequence.NULL
  var isDefined:              Boolean       = false

  override def segments: Array[BasedSequence] =
    if (isReferenceTextCombined) {
      Array(
        referenceOpeningMarker,
        reference,
        referenceClosingMarker,
        textOpeningMarker,
        text,
        textClosingMarker
      )
    } else {
      Array(
        textOpeningMarker,
        text,
        textClosingMarker,
        referenceOpeningMarker,
        reference,
        referenceClosingMarker
      )
    }

  override def astExtra(out: StringBuilder): Unit =
    if (isReferenceTextCombined) {
      Node.delimitedSegmentSpanChars(out, referenceOpeningMarker, reference, referenceClosingMarker, "reference")
      Node.delimitedSegmentSpanChars(out, textOpeningMarker, text, textClosingMarker, "text")
    } else {
      Node.delimitedSegmentSpanChars(out, textOpeningMarker, text, textClosingMarker, "text")
      Node.delimitedSegmentSpanChars(out, referenceOpeningMarker, reference, referenceClosingMarker, "reference")
    }

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(
    textOpeningMarker:      BasedSequence,
    text:                   BasedSequence,
    textClosingMarker:      BasedSequence,
    referenceOpeningMarker: BasedSequence,
    reference:              BasedSequence,
    referenceClosingMarker: BasedSequence
  ) = {
    this()
    this.chars = textOpeningMarker.baseSubSequence(textOpeningMarker.startOffset, referenceClosingMarker.endOffset)
    this.textOpeningMarker = textOpeningMarker
    this.text = text
    this.textClosingMarker = textClosingMarker
    this.referenceOpeningMarker = referenceOpeningMarker
    this.reference = reference
    this.referenceClosingMarker = referenceClosingMarker
  }

  def this(
    chars:                  BasedSequence,
    textOpeningMarker:      BasedSequence,
    text:                   BasedSequence,
    textClosingMarker:      BasedSequence,
    referenceOpeningMarker: BasedSequence,
    reference:              BasedSequence,
    referenceClosingMarker: BasedSequence
  ) = {
    this()
    this.chars = chars
    this.textOpeningMarker = textOpeningMarker
    this.text = text
    this.textClosingMarker = textClosingMarker
    this.referenceOpeningMarker = referenceOpeningMarker
    this.reference = reference
    this.referenceClosingMarker = referenceClosingMarker
  }

  def this(textOpeningMarker: BasedSequence, text: BasedSequence, textClosingMarker: BasedSequence) = {
    this()
    this.chars = textOpeningMarker.baseSubSequence(textOpeningMarker.startOffset, textClosingMarker.endOffset)
    this.textOpeningMarker = textOpeningMarker
    this.text = text
    this.textClosingMarker = textClosingMarker
  }

  def this(chars: BasedSequence, textOpeningMarker: BasedSequence, text: BasedSequence, textClosingMarker: BasedSequence) = {
    this()
    this.chars = chars
    this.textOpeningMarker = textOpeningMarker
    this.text = text
    this.textClosingMarker = textClosingMarker
  }

  def this(
    textOpeningMarker:      BasedSequence,
    text:                   BasedSequence,
    textClosingMarker:      BasedSequence,
    referenceOpeningMarker: BasedSequence,
    referenceClosingMarker: BasedSequence
  ) = {
    this()
    this.chars = textOpeningMarker.baseSubSequence(textOpeningMarker.startOffset, referenceClosingMarker.endOffset)
    this.textOpeningMarker = textOpeningMarker
    this.text = text
    this.textClosingMarker = textClosingMarker
    this.referenceOpeningMarker = referenceOpeningMarker
    this.referenceClosingMarker = referenceClosingMarker
  }

  def setReferenceChars(referenceChars: BasedSequence): Unit = {
    val referenceCharsLength = referenceChars.length
    val openingOffset        = if (referenceChars.charAt(0) == '!') 2 else 1
    this.referenceOpeningMarker = referenceChars.subSequence(0, openingOffset)
    this.reference = referenceChars.subSequence(openingOffset, referenceCharsLength - 1).trim()
    this.referenceClosingMarker = referenceChars.subSequence(referenceCharsLength - 1, referenceCharsLength)
  }

  def setTextChars(textChars: BasedSequence): Unit = {
    val textCharsLength = textChars.length
    this.textOpeningMarker = textChars.subSequence(0, 1)
    this.text = textChars.subSequence(1, textCharsLength - 1).trim()
    this.textClosingMarker = textChars.subSequence(textCharsLength - 1, textCharsLength)
  }

  def isReferenceTextCombined: Boolean =
    text eq BasedSequence.NULL

  override def isTentative: Boolean = !isDefined

  def isDummyReference: Boolean =
    (textOpeningMarker ne BasedSequence.NULL) && (text eq BasedSequence.NULL) && (textClosingMarker ne BasedSequence.NULL)

  override def getReferenceNode(document: Document): Reference =
    getReferenceNode(ssg.md.parser.Parser.REFERENCES.get(Nullable(document: ssg.md.util.data.DataHolder)))

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  override def getReferenceNode(repository: ReferenceRepository): Reference =
    if (repository == null) null.asInstanceOf[Reference] // @nowarn - Java interop boundary
    else {
      val normalizeRef = repository.normalizeKey(reference)
      repository.get(normalizeRef).asInstanceOf[Reference] // @nowarn - may return null
    }

  def dummyReference: BasedSequence =
    if (isDummyReference) {
      chars.baseSubSequence(textOpeningMarker.startOffset, textClosingMarker.endOffset)
    } else {
      BasedSequence.NULL
    }

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean = boundary {
    // images no longer add alt text

    val urlType = flags & TextContainer.F_LINK_TEXT_TYPE

    if (urlType == TextContainer.F_LINK_TEXT) {
      // To allow using leading/trailing spaces for generating heading ids, need to include stripped out spaces
      if (BitFieldSet.any(flags, TextContainer.F_FOR_HEADING_ID) && this.isInstanceOf[ImageRef]) {
        false
      } else if (BitFieldSet.any(flags, TextContainer.F_FOR_HEADING_ID) && BitFieldSet.any(flags, TextContainer.F_NO_TRIM_REF_TEXT_START | TextContainer.F_NO_TRIM_REF_TEXT_END)) {
        val segs = segments
        if (BitFieldSet.any(flags, TextContainer.F_NO_TRIM_REF_TEXT_START)) out.append(chars.baseSubSequence(segs(0).endOffset, segs(1).startOffset))
        nodeVisitor.visitChildren(this)
        if (BitFieldSet.any(flags, TextContainer.F_NO_TRIM_REF_TEXT_END)) out.append(chars.baseSubSequence(segs(1).endOffset, segs(2).startOffset))
        false
      } else {
        true
      }
    } else {
      val ref = getReferenceNode(document)
      if (urlType == TextContainer.F_LINK_NODE_TEXT) {
        out.append(chars)
      } else {
        if (ref == null) { // @nowarn - Java interop: getReferenceNode may return null
          break(true)
        } else {
          val refVal = ref
          val urlSeq: BasedSequence = urlType match {
            case TextContainer.F_LINK_PAGE_REF => refVal.pageRef
            case TextContainer.F_LINK_ANCHOR   => refVal.anchorRef
            case TextContainer.F_LINK_URL      => refVal.url
            case _                             => break(true)
          }

          val textMapper     = new ReplacedTextMapper(urlSeq)
          val unescaped      = Escaping.unescape(urlSeq, textMapper)
          val percentDecoded = Escaping.percentDecodeUrl(unescaped)
          out.append(percentDecoded)
        }
      }

      false
    }
  }

  override protected def toStringAttributes: String =
    "text=" + text + ", reference=" + reference
}
