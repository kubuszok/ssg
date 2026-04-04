/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/AbbreviationBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package abbreviation

import ssg.md.Nullable
import ssg.md.ext.abbreviation.internal.AbbreviationRepository
import ssg.md.util.ast.{ Block, Node, ReferenceNode }
import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }

import scala.language.implicitConversions

/** A block node that contains the abbreviation definition
  */
class AbbreviationBlock() extends Block, ReferenceNode[AbbreviationRepository, AbbreviationBlock, Abbreviation] {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var text:          BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL
  var abbreviation:  BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def referencingNode(node: Node): Nullable[Abbreviation] =
    node match {
      case abbr: Abbreviation => Nullable(abbr)
      case _ => Nullable.empty
    }

  override def compareTo(other: AbbreviationBlock): Int =
    SequenceUtils.compare(text, other.text, true)

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpan(out, openingMarker, "open")
    Node.segmentSpan(out, text, "text")
    Node.segmentSpan(out, closingMarker, "close")
    Node.segmentSpan(out, abbreviation, "abbreviation")
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker, abbreviation)
}
