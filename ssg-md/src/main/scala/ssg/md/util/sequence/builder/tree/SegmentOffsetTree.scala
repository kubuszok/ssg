/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/SegmentOffsetTree.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/SegmentOffsetTree.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package builder
package tree

import ssg.md.Nullable
import ssg.md.util.misc.DelimitedBuilder
import ssg.md.util.sequence.BasedSequence

/** Segment tree which uses offsets instead of aggregated length of segments
  *
  * Used to find original base offsets in SegmentedSequence result
  *
  * NOTE: although it is a SegmentTree, most of the SegmentTree functions use index into sequence for context and cannot be used with offset data. Their use will throw [[IllegalStateException]] if
  * invoked.
  */
class SegmentOffsetTree private[tree] (
  treeData:         Array[Int],
  segmentBytes:     Array[Byte],
  val startIndices: Array[Int]
) extends SegmentTree(treeData, segmentBytes) {

  def endOffset(pos: Int): Int =
    super.aggrLength(pos)

  def getStartIndex(pos: Int): Int =
    if (pos < 0) 0
    else if (pos >= startIndices.length) startIndices(startIndices.length - 1)
    else startIndices(pos)

  override def getSegment(pos: Int, baseSeq: BasedSequence): Segment =
    Segment.getSegment(segmentBytes, byteOffset(pos), pos, startIndices(pos), baseSeq)

  def findSegmentPosByOffset(offset: Int): Nullable[SegmentTreePos] =
    SegmentTree.findSegmentPos(offset, treeData, 0, size)

  def getPreviousText(segment: Segment, baseSeq: BasedSequence): Nullable[Segment] =
    if (segment.pos == 0) {
      if (segment.startIndex > 0) {
        val textSeg = getSegment(0, -1, 0, baseSeq)
        if (textSeg.isText) Nullable(textSeg) else Nullable.empty
      } else {
        Nullable.empty
      }
    } else {
      val prevSegment = getSegment(segment.pos - 1, baseSeq)
      getNextText(prevSegment, baseSeq)
    }

  def getNextText(segment: Segment, baseSeq: BasedSequence): Nullable[Segment] =
    if (segment.byteOffset + segment.getByteLength < segmentBytes.length) {
      val textSeg = getSegment(segment.byteOffset + segment.getByteLength, -1, segment.endIndex, baseSeq)
      if (textSeg.isText) Nullable(textSeg) else Nullable.empty
    } else {
      Nullable.empty
    }

  def findSegmentByOffset(offset: Int, baseSeq: BasedSequence, hint: Nullable[Segment]): Nullable[Segment] = {
    val treePos = super.findSegmentPos(offset, 0, size)
    treePos.map { tp =>
      Segment.getSegment(segmentBytes, byteOffset(tp.pos), tp.pos, startIndices(tp.pos), baseSeq)
    }
  }

  override def toString(baseSeq: BasedSequence): String = {
    val out = new DelimitedBuilder(", ")
    out.append(getClass.getSimpleName).append("{aggr: {")
    val iMax = size
    var i    = 0
    while (i < iMax) {
      out.append("[").append(aggrLength(i)).append(", ").append(byteOffset(i)).append(":")
      out.append(", :").append(startIndices(i))
      out.append("]").mark()
      i += 1
    }

    out.unmark().append(" }, seg: { ")
    var offset = 0
    while (offset < segmentBytes.length) {
      val segment = Segment.getSegment(segmentBytes, offset, 0, 0, baseSeq)
      out.append(offset).append(":").append(segment).mark()
      offset += segment.getByteLength
    }
    out.unmark().append(" } }")
    out.toString
  }

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def hasPreviousAnchor(pos: Int): Boolean = false

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def previousAnchorOffset(pos: Int): Int = 0

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def aggrLength(pos: Int): Int =
    // NOTE: used by toString() so can only deprecate
    super.aggrLength(pos)

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def findSegmentPos(index: Int): Nullable[SegmentTreePos] =
    throw new IllegalStateException("Method in SegmentOffsetTree should not be used")

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def findSegment(index: Int, baseSeq: BasedSequence, hint: Nullable[Segment]): Nullable[Segment] =
    throw new IllegalStateException("Method in SegmentOffsetTree should not be used")

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def findSegment(index: Int, startPos: Int, endPos: Int, baseSeq: BasedSequence, hint: Nullable[Segment]): Nullable[Segment] =
    throw new IllegalStateException("Method in SegmentOffsetTree should not be used")

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def getSegmentRange(startIndex: Int, endIndex: Int, startPos: Int, endPos: Int, baseSeq: BasedSequence, hint: Nullable[Segment]): SegmentTreeRange =
    super.getSegmentRange(startIndex, endIndex, startPos, endPos, baseSeq, hint)

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def addSegments(builder: IBasedSegmentBuilder[?], treeRange: SegmentTreeRange): Unit =
    throw new IllegalStateException("Method in SegmentOffsetTree should not be used")

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def addSegments(builder: IBasedSegmentBuilder[?], startIndex: Int, endIndex: Int, startOffset: Int, endOffset: Int, startPos: Int, endPos: Int): Unit =
    throw new IllegalStateException("Method in SegmentOffsetTree should not be used")

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def findSegmentPos(index: Int, startPos: Int, endPos: Int): Nullable[SegmentTreePos] =
    throw new IllegalStateException("Method in SegmentOffsetTree should not be used")

  @deprecated("Method in SegmentOffsetTree should not be used", "")
  override def getPrevAnchor(pos: Int, baseSeq: BasedSequence): Nullable[Segment] =
    throw new IllegalStateException("Method in SegmentOffsetTree should not be used")
}

object SegmentOffsetTree {

  def build(segments: java.lang.Iterable[Seg], allText: CharSequence): SegmentOffsetTree = {
    val segmentTreeData = SegmentTree.buildTreeData(segments, allText, buildIndexData = false)
    assert(segmentTreeData.startIndices.isDefined)
    new SegmentOffsetTree(segmentTreeData.treeData, segmentTreeData.segmentBytes, segmentTreeData.startIndices.get)
  }

  def build(builder: BasedSegmentBuilder): SegmentOffsetTree = {
    val segmentTreeData = SegmentTree.buildTreeData(builder.getSegments, builder.getText, buildIndexData = true)
    new SegmentTree(segmentTreeData.treeData, segmentTreeData.segmentBytes).getSegmentOffsetTree(builder.baseSequence)
  }

  def build(baseSeq: BasedSequence): SegmentOffsetTree =
    baseSeq.getSegmentTree.getSegmentOffsetTree(baseSeq)
}
