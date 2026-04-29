/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/SpaceInsertingSequenceBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/SpaceInsertingSequenceBuilder.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.misc.BitFieldSet
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.Range
import ssg.md.util.sequence.builder.BasedSegmentBuilder
import ssg.md.util.sequence.builder.ISequenceBuilder
import ssg.md.util.sequence.builder.SegmentOptimizer
import ssg.md.util.sequence.builder.SequenceBuilder

import scala.util.boundary
import scala.util.boundary.break

class SpaceInsertingSequenceBuilder private (
  val out:                   SequenceBuilder,
  val addSpacesBetweenNodes: Boolean
) extends ISequenceBuilder[SpaceInsertingSequenceBuilder, BasedSequence] {

  private var _lastNode:  Nullable[Node] = Nullable.empty
  private var _needEol:   Boolean        = false
  private var _addSpaces: Boolean        = false

  def isNeedEol: Boolean = _needEol

  def needEol_=(needEol: Boolean): Unit =
    _needEol = needEol

  def lastNode: Nullable[Node] = _lastNode

  def lastNode_=(lastNode: Node): Unit =
    if (lastNode.isInstanceOf[Document]) {
      () // skip Document nodes
    } else {
      if (_lastNode.isDefined && _lastNode.get.endOffset < lastNode.startOffset) {
        val sequence = baseSequence.subSequence(_lastNode.get.endOffset, lastNode.startOffset)
        _needEol = sequence.trim(CharPredicate.SPACE_TAB).length > 0 && sequence.trim(CharPredicate.WHITESPACE).isEmpty
      }

      _addSpaces = addSpacesBetweenNodes
      _lastNode = Nullable(lastNode)
    }

  def needSpace(): Boolean = boundary {
    var partIndex = out.segmentBuilder.size
    while (partIndex >= 0) {
      val part = out.segmentBuilder.getPart(partIndex)
      part match {
        case range: Range =>
          if (range.isNotNull) {
            val sequence = baseSequence.subSequence(range.start, range.end)
            if (sequence.length > 0) {
              break(!CharPredicate.WHITESPACE.test(sequence.charAt(sequence.length - 1)))
            }
          }
        case cs: CharSequence =>
          if (cs.length > 0) {
            break(!CharPredicate.WHITESPACE.test(cs.charAt(cs.length - 1)))
          }
        case _ =>
          throw new IllegalStateException("Invalid part type " + part.getClass.getSimpleName)
      }

      partIndex -= 1
    }
    false
  }

  def appendEol(): Unit = {
    append('\n')
    _needEol = false
  }

  def needEol(): Boolean = boundary {
    if (_needEol) {
      break(true)
    }
    var partIndex = out.segmentBuilder.size
    while (partIndex >= 0) {
      val part = out.segmentBuilder.getPart(partIndex)
      part match {
        case range: Range =>
          if (range.isNotNull) {
            val sequence = baseSequence.subSequence(range.start, range.end)
            if (sequence.length > 0) {
              break(!CharPredicate.EOL.test(sequence.charAt(sequence.length - 1)))
            }
          }
        case cs: CharSequence =>
          if (cs.length > 0) {
            break(!CharPredicate.EOL.test(cs.charAt(cs.length - 1)))
          }
        case _ =>
          throw new IllegalStateException("Invalid part type " + part.getClass.getSimpleName)
      }

      partIndex -= 1
    }
    false
  }

  def baseSequence: BasedSequence = out.baseSequence

  def segmentBuilder: BasedSegmentBuilder = out.segmentBuilder

  override def singleBasedSequence: Nullable[BasedSequence] = out.singleBasedSequence

  override def getBuilder: SpaceInsertingSequenceBuilder = new SpaceInsertingSequenceBuilder(out.getBuilder, addSpacesBetweenNodes)

  override def append(chars: Nullable[CharSequence], startIndex: Int, endIndex: Int): SpaceInsertingSequenceBuilder = {
    if (_addSpaces && chars.isDefined && startIndex < endIndex && !CharPredicate.WHITESPACE.test(chars.get.charAt(startIndex)) && needSpace()) {
      out.append(' ')
      _addSpaces = false
    }
    out.append(chars, startIndex, endIndex)
    this
  }

  override def append(c: Char): SpaceInsertingSequenceBuilder = {
    if (_addSpaces && !CharPredicate.WHITESPACE.test(c) && needSpace()) {
      out.append(' ')
      _addSpaces = false
    }
    out.append(c)
    this
  }

  override def append(c: Char, count: Int): SpaceInsertingSequenceBuilder = {
    if (_addSpaces && !CharPredicate.WHITESPACE.test(c) && needSpace()) {
      out.append(' ')
      _addSpaces = false
    }
    out.append(c, count)
    this
  }

  def append(startOffset: Int, endOffset: Int): SpaceInsertingSequenceBuilder = {
    if (_addSpaces && startOffset < endOffset && !CharPredicate.WHITESPACE.test(out.baseSequence.charAt(startOffset)) && needSpace()) {
      out.append(' ')
      _addSpaces = false
    }
    out.append(startOffset, endOffset)
    this
  }

  def append(chars: Range): SpaceInsertingSequenceBuilder = append(chars.start, chars.end)

  def addRange(range: Range): SpaceInsertingSequenceBuilder = append(range.start, range.end)

  def addByOffsets(startOffset: Int, endOffset: Int): SpaceInsertingSequenceBuilder = append(startOffset, endOffset)

  def addByLength(startOffset: Int, textLength: Int): SpaceInsertingSequenceBuilder = append(startOffset, startOffset + textLength)

  override def toSequence: BasedSequence = out.toSequence

  override def length: Int = out.length

  override def charAt(index: Int): Char = out.charAt(index)

  def toStringWithRanges: String = out.toStringWithRanges

  override def toString: String = out.toString

  def toStringNoAddedSpaces: String = out.toStringNoAddedSpaces

  override def addAll(sequences: Iterable[? <: CharSequence]): SpaceInsertingSequenceBuilder =
    append(sequences)

  override def append(sequences: Iterable[? <: CharSequence]): SpaceInsertingSequenceBuilder = {
    sequences.foreach { sequence =>
      append(Nullable(sequence))
    }
    this
  }

  override def add(chars: Nullable[CharSequence]): SpaceInsertingSequenceBuilder = append(chars)

  override def append(chars: Nullable[CharSequence]): SpaceInsertingSequenceBuilder =
    if (chars.isEmpty) this
    else append(chars, 0, chars.get.length)

  override def append(chars: Nullable[CharSequence], startIndex: Int): SpaceInsertingSequenceBuilder =
    if (chars.isEmpty) this
    else append(chars, startIndex, chars.get.length)
}

object SpaceInsertingSequenceBuilder {

  def emptyBuilder(base: BasedSequence): SpaceInsertingSequenceBuilder =
    new SpaceInsertingSequenceBuilder(SequenceBuilder.emptyBuilder(base), false)

  def emptyBuilder(base: BasedSequence, optimizer: SegmentOptimizer): SpaceInsertingSequenceBuilder =
    new SpaceInsertingSequenceBuilder(SequenceBuilder.emptyBuilder(base, optimizer), false)

  def emptyBuilder(base: BasedSequence, options: Int): SpaceInsertingSequenceBuilder =
    new SpaceInsertingSequenceBuilder(SequenceBuilder.emptyBuilder(base, options), BitFieldSet.any(options, TextContainer.F_ADD_SPACES_BETWEEN_NODES))

  def emptyBuilder(base: BasedSequence, options: Int, optimizer: SegmentOptimizer): SpaceInsertingSequenceBuilder =
    new SpaceInsertingSequenceBuilder(
      SequenceBuilder.emptyBuilder(base, options, optimizer),
      BitFieldSet.any(options, TextContainer.F_ADD_SPACES_BETWEEN_NODES)
    )

  def emptyBuilder(builder: SequenceBuilder): SpaceInsertingSequenceBuilder =
    new SpaceInsertingSequenceBuilder(builder, false)
}
