/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SegmentedSequenceTree.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.sequence.builder.{ IBasedSegmentBuilder, ISegmentBuilder }
import ssg.md.util.sequence.builder.tree.{ Segment, SegmentTree, SegmentTreeRange }

/** A BasedSequence which consists of segments of other BasedSequences NOTE: uses tree-based lookup for efficient random access with minimal memory overhead.
  */
final class SegmentedSequenceTree private (
  baseSeq:                 BasedSequence,
  startOffset:             Int,
  endOffset:               Int,
  length:                  Int,
  private val segmentTree: SegmentTree,
  private val startIndex:  Int,
  private val startPos:    Int,
  private val endPos:      Int
) extends SegmentedSequence(baseSeq, startOffset, endOffset, length) {

  private val cache: ThreadLocal[Nullable[SegmentedSequenceTree.Cache]] =
    ThreadLocal.withInitial(() => Nullable.empty[SegmentedSequenceTree.Cache])

  // Constructor for original sequences from builder
  private def this(baseSeq: BasedSequence, startOffset: Int, endOffset: Int, length: Int, segmentTree: SegmentTree) = {
    this(baseSeq, startOffset, endOffset, length, segmentTree, 0, 0, segmentTree.size)
  }

  // Constructor for sub-sequences via SegmentTreeRange
  private def this(baseSeq: BasedSequence, segmentTree: SegmentTree, subSequenceRange: SegmentTreeRange) = {
    this(
      baseSeq,
      subSequenceRange.startOffset,
      subSequenceRange.endOffset,
      subSequenceRange.length,
      segmentTree,
      subSequenceRange.startIndex,
      subSequenceRange.startPos,
      subSequenceRange.endPos
    )
  }

  private def getCache(index: Int): SegmentedSequenceTree.Cache = {
    val c = cache.get()
    if (!c.isDefined || c.get.segment.notInSegment(index + startIndex)) {
      val prevSegment: Nullable[Segment] = if (c.isDefined) Nullable(c.get.segment) else Nullable.empty[Segment]
      val segment = segmentTree.findSegment(index + startIndex, startPos, endPos, this.baseSeq, prevSegment)
      assert(segment.isDefined)
      val newCache = new SegmentedSequenceTree.Cache(segment.get, segment.get.getCharSequence, startIndex)
      cache.set(Nullable(newCache))
      newCache
    } else {
      c.get
    }
  }

  private def getCachedSegment: Nullable[Segment] = {
    val c = cache.get()
    if (c.isDefined) Nullable(c.get.segment) else Nullable.empty[Segment]
  }

  override def getIndexOffset(index: Int): Int =
    if (index == _length) {
      val c            = getCache(index - 1)
      val charSequence = c.chars
      charSequence match {
        case bs: BasedSequence => bs.getIndexOffset(c.charIndex(index))
        case _ => -1
      }
    } else {
      SequenceUtils.validateIndexInclusiveEnd(index, length)
      val c            = getCache(index)
      val charSequence = c.chars
      charSequence match {
        case bs: BasedSequence => bs.getIndexOffset(c.charIndex(index))
        case _ => -1
      }
    }

  override def addSegments(builder: IBasedSegmentBuilder[?]): Unit =
    segmentTree.addSegments(builder, startIndex, startIndex + _length, _startOffset, _endOffset, startPos, endPos)

  override def getSegmentTree: SegmentTree = segmentTree

  override def charAt(index: Int): Char = {
    SequenceUtils.validateIndex(index, length)
    getCache(index).charAt(index)
  }

  override def subSequence(startIndex: Int, endIndex: Int): BasedSequence =
    if (startIndex == 0 && endIndex == _length) {
      this
    } else {
      SequenceUtils.validateStartEnd(startIndex, endIndex, length)
      val subSequenceRange = segmentTree.getSegmentRange(
        startIndex + this.startIndex,
        endIndex + this.startIndex,
        startPos,
        endPos,
        this.baseSeq,
        getCachedSegment
      )
      new SegmentedSequenceTree(this.baseSeq, segmentTree, subSequenceRange)
    }
}

object SegmentedSequenceTree {

  final private class Cache(val segment: Segment, val chars: CharSequence, startIndex: Int) {
    private val indexDelta: Int = startIndex - segment.startIndex

    def charAt(index: Int): Char = chars.charAt(index + indexDelta)

    def charIndex(index: Int): Int = index + indexDelta
  }

  /** Base Constructor
    *
    * @param baseSeq
    *   base sequence
    * @param builder
    *   builder containing segments for this sequence
    * @return
    *   segmented sequence
    */
  def create(baseSeq: BasedSequence, builder: ISegmentBuilder[?]): SegmentedSequenceTree = {
    val segmentTree = SegmentTree.build(builder.getSegments, builder.getText)

    if (baseSeq.anyOptions(BasedOptionsHolder.F_COLLECT_SEGMENTED_STATS)) {
      val stats = baseSeq.getOption(BasedOptionsHolder.SEGMENTED_STATS)
      if (stats.isDefined) {
        stats.get.addStats(builder.noAnchorsSize, builder.length, segmentTree.treeData.length * 4 + segmentTree.segmentBytes.length)
      }
    }

    new SegmentedSequenceTree(baseSeq.getBaseSequence, builder.startOffset, builder.endOffset, builder.length, segmentTree)
  }
}
