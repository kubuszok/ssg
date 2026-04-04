/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/MacroDefinitionBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package macros

import ssg.md.Nullable
import ssg.md.ext.macros.internal.MacroDefinitionRepository
import ssg.md.util.ast.{ Block, BlockContent, Node, ReferenceNode }
import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }

import scala.language.implicitConversions

/** A MacroReference block node */
class MacroDefinitionBlock() extends Block, ReferenceNode[MacroDefinitionRepository, MacroDefinitionBlock, MacroReference] {

  var openingMarker:        BasedSequence = BasedSequence.NULL
  var name:                 BasedSequence = BasedSequence.NULL
  var openingTrailing:      BasedSequence = BasedSequence.NULL
  var closingMarker:        BasedSequence = BasedSequence.NULL
  var closingTrailing:      BasedSequence = BasedSequence.NULL
  var ordinal:              Int           = 0
  var firstReferenceOffset: Int           = Integer.MAX_VALUE
  var footnoteReferences:   Int           = 0
  var inExpansion:          Boolean       = false

  def addFirstReferenceOffset(offset: Int): Unit =
    if (this.firstReferenceOffset < offset) this.firstReferenceOffset = offset

  def isReferenced: Boolean = this.firstReferenceOffset < Integer.MAX_VALUE

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpanChars(out, openingMarker, "open")
    Node.segmentSpanChars(out, name, "name")
    Node.segmentSpanChars(out, openingTrailing, "openTrail")
    Node.segmentSpanChars(out, closingMarker, "close")
    Node.segmentSpanChars(out, closingTrailing, "closeTrail")
  }

  override def segments: Array[BasedSequence] = Array(openingMarker, name, openingTrailing, closingMarker, closingTrailing)

  override def referencingNode(node: Node): Nullable[MacroReference] =
    node match {
      case ref: MacroReference => Nullable(ref)
      case _ => Nullable.empty
    }

  override def compareTo(other: MacroDefinitionBlock): Int =
    SequenceUtils.compare(name, other.name, true)

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, segments: java.util.List[BasedSequence]) = {
    this()
    this.chars = chars
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }
}
