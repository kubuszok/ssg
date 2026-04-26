/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferenceBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferenceBase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package enumerated
package reference

import ssg.md.Nullable
import ssg.md.util.ast.{ DelimitedNode, DoNotDecorate, Document, Node, ReferencingNode }
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

/** A EnumeratedReference node */
class EnumeratedReferenceBase() extends Node, DelimitedNode, DoNotDecorate, ReferencingNode[EnumeratedReferenceRepository, EnumeratedReferenceBlock] {

  var openingMarker:                               BasedSequence                      = BasedSequence.NULL
  var text:                                        BasedSequence                      = BasedSequence.NULL
  var closingMarker:                               BasedSequence                      = BasedSequence.NULL
  private[reference] var enumeratedReferenceBlock: Nullable[EnumeratedReferenceBlock] = Nullable.empty

  override def reference: BasedSequence = text

  override def getReferenceNode(document: Document): EnumeratedReferenceBlock =
    enumeratedReferenceBlock.getOrElse(null).asInstanceOf[EnumeratedReferenceBlock] // @nowarn - Java interop: may return null

  override def getReferenceNode(repository: EnumeratedReferenceRepository): EnumeratedReferenceBlock =
    if (enumeratedReferenceBlock.isDefined || text.isEmpty) {
      enumeratedReferenceBlock.getOrElse(null).asInstanceOf[EnumeratedReferenceBlock] // @nowarn - Java interop: may return null
    } else {
      enumeratedReferenceBlock = Nullable(getEnumeratedReferenceBlock(repository))
      enumeratedReferenceBlock.getOrElse(null).asInstanceOf[EnumeratedReferenceBlock] // @nowarn - Java interop: may return null
    }

  override def isDefined: Boolean = enumeratedReferenceBlock.isDefined

  def getEnumeratedReferenceBlock(enumeratedReferenceRepository: EnumeratedReferenceRepository): EnumeratedReferenceBlock =
    if (text.isEmpty) null.asInstanceOf[EnumeratedReferenceBlock] // @nowarn - Java interop boundary with NodeRepository.get
    else enumeratedReferenceRepository.get(EnumeratedReferenceRepository.getType(text.toString))

  def enumeratedReferenceBlock_=(block: EnumeratedReferenceBlock): Unit =
    this.enumeratedReferenceBlock = Nullable(block)

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit =
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")

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
}
