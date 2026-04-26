/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/SegmentTree.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/SegmentTree.java
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

import java.util.Arrays
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** Binary search tree of sequence segments
  */
class SegmentTree private[tree] (
  val treeData:     Array[Int], // tuples of aggregated length, segment byte offset with flags for prev anchor offset of 1 to 7
  val segmentBytes: Array[Byte] // bytes of serialized segments
) {

  def size: Int = treeData.length / 2

  def aggrLength(pos: Int): Int =
    if (pos < 0) 0 else treeData(pos << 1)

  def byteOffsetData(pos: Int): Int =
    treeData((pos << 1) + 1)

  def byteOffset(pos: Int): Int =
    SegmentTree.getByteOffset(treeData((pos << 1) + 1))

  def hasPreviousAnchor(pos: Int): Boolean =
    SegmentTree.getAnchorOffset(treeData((pos << 1) + 1)) > 0

  def previousAnchorOffset(pos: Int): Int = {
    val bod = byteOffsetData(pos)
    SegmentTree.getByteOffset(bod) - SegmentTree.getAnchorOffset(bod)
  }

  def findSegmentPos(index: Int): Nullable[SegmentTreePos] =
    SegmentTree.findSegmentPos(index, treeData, 0, size)

  def getSegment(byteOff: Int, pos: Int, startIndex: Int, baseSeq: BasedSequence): Segment =
    Segment.getSegment(segmentBytes, byteOff, pos, startIndex, baseSeq)

  def findSegment(index: Int, baseSeq: BasedSequence, hint: Nullable[Segment]): Nullable[Segment] =
    findSegment(index, 0, size, baseSeq, hint)

  def findSegment(index: Int, startPos: Int, endPos: Int, baseSeq: BasedSequence, hint: Nullable[Segment]): Nullable[Segment] = boundary {
    var sp = startPos
    var ep = endPos

    hint.foreach { h =>
      // NOTE: first try around cached segment for this index
      val si = h.startIndex
      if (index >= si) {
        val ei = h.endIndex
        assert(
          index >= ei,
          String.format(
            "FindSegment should not be called, index %d is in range [%d, %d) of hint segment: %s",
            index: Integer,
            si:    Integer,
            ei:    Integer,
            h.toString
          )
        )
        if (h.pos + 1 >= ep) {
          // beyond last segment
        } else {
          val nextLength = aggrLength(h.pos + 1)
          if (index < nextLength) {
            // FIX: add stats to track this
            break(Nullable(Segment.getSegment(segmentBytes, byteOffset(h.pos + 1), h.pos + 1, ei, baseSeq)))
          }
          // can skip next one too
          sp = h.pos + 2
        }
      } else {
        // see if previous contains index
        if (h.pos == sp) {
          // before first segment
        } else {
          val prevPrevLength = aggrLength(h.pos - 2)
          if (index >= prevPrevLength) {
            // it is previous one
            // FIX: add stats to track this
            break(Nullable(Segment.getSegment(segmentBytes, byteOffset(h.pos - 1), h.pos - 1, prevPrevLength, baseSeq)))
          }
          // previous one can be skipped
          ep = h.pos - 1
        }
      }
    }

    // NOTE: most of the time char sequence access starts at 0, so we try the start pos
    if (sp >= 0 && sp < size) {
      val firstLength = aggrLength(sp)
      if (index < firstLength) {
        val prevLength = aggrLength(sp - 1)
        if (index >= prevLength) {
          // FIX: add stats to track this
          break(Nullable(Segment.getSegment(segmentBytes, byteOffset(sp), sp, prevLength, baseSeq)))
        }
        // first one is too far, we can skip it
        ep = sp
      } else {
        // first one can be skipped
        sp = sp + 1
      }
    }

    // NOTE: failing that we try the last segment in case it is backwards scan through sequence
    if (ep - 1 >= sp) {
      // check last one for match
      val secondToLastLength = aggrLength(ep - 2)
      if (index >= secondToLastLength) {
        val lastLength = aggrLength(ep - 1)
        if (index >= lastLength) {
          break(Nullable.empty[Segment]) /* beyond last segment */
        }
        // FIX: add stats to track this
        break(Nullable(Segment.getSegment(segmentBytes, byteOffset(ep - 1), ep - 1, secondToLastLength, baseSeq)))
      } else {
        // previous to last can be skipped
        ep = ep - 1
      }
    }

    // NOTE: all optimizations failed, but not completely wasted since they served to shorten the search range.
    val treePos = SegmentTree.findSegmentPos(index, treeData, sp, ep)
    treePos.fold(Nullable.empty[Segment]) { tp =>
      Segment.getSegment(segmentBytes, byteOffset(tp.pos), tp.pos, tp.startIndex, baseSeq)
    }
  }

  def getSegmentRange(startIndex: Int, endIndex: Int, startPos: Int, endPos: Int, baseSequence: BasedSequence, hint: Nullable[Segment]): SegmentTreeRange = {
    var startSegment: Segment = null.asInstanceOf[Segment] // safe: always assigned before use
    var endSegment:   Segment = null.asInstanceOf[Segment]

    if (startIndex == endIndex) {
      // this could be an empty suffix so it may be the end of a segment, search for startIndex-1 and use that segment as its location
      startSegment = {
        val h = hint.filter(!_.notInSegment(startIndex))
        h.getOrElse(findSegment(startIndex, startPos, endPos, baseSequence, hint).getOrElse(null.asInstanceOf[Segment]))
      }
      if (startSegment == null.asInstanceOf[Segment]) {
        assert(startIndex > 0)

        startSegment = {
          val h = hint.filter(!_.notInSegment(startIndex - 1))
          h.getOrElse(findSegment(startIndex - 1, startPos, endPos, baseSequence, hint).get)
        }

        // if index is out of the found segment and there is a next segment which contains start index, then use that one
        if (startSegment.notInSegment(startIndex) && startSegment.pos + 1 < size) {
          val nextSeg = getSegment(startSegment.pos + 1, baseSequence)
          if (!nextSeg.notInSegment(startIndex)) {
            startSegment = nextSeg
          }
        }
      }

      endSegment = startSegment
    } else {
      startSegment = {
        val h = hint.filter(!_.notInSegment(startIndex))
        h.getOrElse(findSegment(startIndex, startPos, endPos, baseSequence, hint).get)
      }
      endSegment =
        if (!startSegment.notInSegment(endIndex - 1)) {
          startSegment
        } else {
          val h = hint.filter(!_.notInSegment(endIndex - 1))
          h.getOrElse(findSegment(endIndex - 1, startPos, endPos, baseSequence, startSegment).get)
        }
    }

    var startOffset = -1
    var endOffset   = -1

    // if start segment is text then we look for previous anchor or range to get startOffset base context information, failing that look for next range or anchor
    if (startSegment.isText) {
      startOffset = getTextStartOffset(startSegment, baseSequence)
    } else {
      startOffset = startSegment.getStartOffset + startIndex - startSegment.startIndex
    }

    // if end segment is text then we look for next anchor or range to get endOffset base context information
    if (endSegment.isText) {
      endOffset = getTextEndOffset(endSegment, baseSequence)
    } else {
      endOffset = endSegment.getStartOffset + endIndex - endSegment.startIndex
    }

    if (startOffset < 0) {
      if (startSegment.pos + 1 < size) {
        val nextSeg = getSegment(startSegment.pos + 1, baseSequence)
        startOffset = nextSeg.getStartOffset
        if (startOffset > endOffset && endOffset != -1) startOffset = endOffset
      } else {
        startOffset = endOffset
      }
    }

    if (endOffset < startOffset) endOffset = startOffset

    if (startOffset > baseSequence.length) {
      throw new IllegalStateException(String.format("startOffset:%d > baseSeq.length: %d", startOffset: Integer, baseSequence.length: Integer))
    }

    if (endOffset > baseSequence.length) {
      throw new IllegalStateException(String.format("endOffset:%d > baseSeq.length: %d", endOffset: Integer, baseSequence.length: Integer))
    }

    new SegmentTreeRange(
      startIndex,
      endIndex,
      startOffset,
      endOffset,
      startSegment.pos,
      endSegment.pos + 1
    )
  }

  def getTextEndOffset(segment: Segment, baseSequence: BasedSequence): Int = {
    assert(segment.isText)

    if (segment.pos + 1 < size) {
      val nextSeg = getSegment(segment.pos + 1, baseSequence)
      if (nextSeg.isBase) nextSeg.getStartOffset
      else -1
    } else {
      -1
    }
  }

  def getTextStartOffset(segment: Segment, baseSequence: BasedSequence): Int = {
    assert(segment.isText)

    var prevSegment: Nullable[Segment] = getPrevAnchor(segment.pos, baseSequence)
    if (prevSegment.isEmpty && segment.pos > 0) {
      prevSegment = getSegment(segment.pos - 1, baseSequence)
    }

    prevSegment.fold(-1) { ps =>
      if (ps.isBase) ps.getEndOffset else -1
    }
  }

  /** Add segments selected by given treeRange
    *
    * @param builder
    *   based segment builder
    * @param treeRange
    *   treeRange for which to add segments
    */
  def addSegments(builder: IBasedSegmentBuilder[?], treeRange: SegmentTreeRange): Unit =
    addSegments(
      builder,
      treeRange.startIndex,
      treeRange.endIndex,
      treeRange.startOffset,
      treeRange.endOffset,
      treeRange.startPos,
      treeRange.endPos
    )

  /** Add segments of subsequence of this tree to builder
    *
    * @param builder
    *   builder to which to add the segments
    * @param startIndex
    *   start index of sub-sequence of segment tree
    * @param endIndex
    *   end index of sub-sequence of segment tree
    * @param startOffset
    *   start offset of the subsequence to use as start anchor
    * @param endOffset
    *   end offset of the subsequence to use as end anchor
    * @param startPos
    *   start pos of sub-sequence segments in tree
    * @param endPos
    *   end pos of sub-sequence segments in tree
    */
  def addSegments(builder: IBasedSegmentBuilder[?], startIndex: Int, endIndex: Int, startOffset: Int, endOffset: Int, startPos: Int, endPos: Int): Unit = {
    // add our stuff to builder
    if (startOffset != -1) {
      builder.appendAnchor(startOffset)
    }

    var currentEnd   = startOffset
    val baseSequence = builder.baseSequence

    var i = startPos
    while (i < endPos) {
      val segment = getSegment(i, baseSequence)

      if (segment.isText) {
        // check for previous anchor
        val prevAnchor = getPrevAnchor(i, baseSequence)
        prevAnchor.foreach(pa => builder.appendAnchor(pa.getStartOffset))
      }

      // OPTIMIZE: add append Segment method with start/end offsets to allow builder to extract repeat and first256 information
      //  without needing to scan text, range information does not have any benefit from this
      val charSequence = SegmentTree.getCharSequence(segment, startIndex, endIndex, startPos, endPos)

      if (segment.isText) {
        builder.append(charSequence)
        // check for next anchor
        val nextByteOffset = segment.byteOffset + segment.getByteLength
        if (nextByteOffset < segmentBytes.length && (i + 1 >= size || nextByteOffset != byteOffset(i + 1))) {
          val nextAnchor = Segment.getSegment(segmentBytes, nextByteOffset, 0, 0, baseSequence)
          if (nextAnchor.isAnchor) {
            builder.appendAnchor(nextAnchor.getStartOffset)
          }
        }
      } else {
        val basedSequence = charSequence.asInstanceOf[BasedSequence]
        currentEnd = Math.max(currentEnd, basedSequence.endOffset)
        builder.append(basedSequence.startOffset, basedSequence.endOffset)
      }

      i += 1
    }

    if (endOffset != -1) {
      builder.appendAnchor(Math.max(currentEnd, endOffset))
    }
  }

  def findSegmentPos(index: Int, startPos: Int, endPos: Int): Nullable[SegmentTreePos] =
    SegmentTree.findSegmentPos(index, treeData, startPos, endPos)

  def getSegment(pos: Int, baseSeq: BasedSequence): Segment =
    Segment.getSegment(segmentBytes, byteOffset(pos), pos, aggrLength(pos - 1), baseSeq)

  def getPrevAnchor(pos: Int, baseSeq: BasedSequence): Nullable[Segment] =
    SegmentTree.getPrevAnchor(pos, treeData, segmentBytes, baseSeq)

  def toString(baseSeq: BasedSequence): String = {
    val out = new DelimitedBuilder(", ")
    out.append(getClass.getSimpleName).append("{aggr: {")
    val iMax = size
    var i    = 0
    while (i < iMax) {
      out.append("[").append(aggrLength(i)).append(", ").append(byteOffset(i)).append(":")
      if (hasPreviousAnchor(i)) {
        out.append(", ").append(previousAnchorOffset(i)).append(":")
      }
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

  override def toString: String = toString(BasedSequence.NULL)

  /** Build an offset segment tree from this index segment tree
    *
    * Efficiently reuses segmentBytes and only computes offset treeData for BASE and ANCHOR segments
    *
    * @param baseSeq
    *   base sequence for the sequence for this segment tree
    * @return
    *   SegmentOffsetTree for this segment tree
    */
  def getSegmentOffsetTree(baseSeq: BasedSequence): SegmentOffsetTree = {
    var nonAnchors    = 0
    val byteLength    = segmentBytes.length
    var segOffset     = 0
    var lastEndOffset = 0

    while (segOffset < byteLength) {
      val seg = Segment.getSegment(segmentBytes, segOffset, nonAnchors, 0, baseSeq)
      segOffset += seg.getByteLength
      if (seg.isBase) {
        nonAnchors += 1
        lastEndOffset = seg.getEndOffset
      }
    }

    val td = new Array[Int](nonAnchors * 2)
    val si = new Array[Int](nonAnchors)

    var pos = 0
    segOffset = 0
    var length                    = 0
    val posNeedingAdjustment      = new Array[Int](2) // up to 2 segment adjustments, one for BASE sequence and one for TEXT since it has no offsets
    var posNeedingAdjustmentIndex = 0

    while (segOffset < byteLength) {
      val seg = Segment.getSegment(segmentBytes, segOffset, nonAnchors, length, baseSeq)

      if (posNeedingAdjustmentIndex > 0 && seg.getStartOffset >= 0) {
        // set it to the correct value
        val iMax = posNeedingAdjustmentIndex
        var j    = 0
        while (j < iMax) {
          td(posNeedingAdjustment(j) << 1) = seg.getStartOffset
          j += 1
        }
        posNeedingAdjustmentIndex = 0
      }

      if (seg.isBase) {
        // the use of getEndOffset here is temporary for all but the last base segment, it will be overwritten by getStartOffset by next segment
        SegmentTree.setTreeData(pos, td, seg.getEndOffset, segOffset, 0)
        posNeedingAdjustment(posNeedingAdjustmentIndex) = pos
        posNeedingAdjustmentIndex += 1
        si(pos) = length

        pos += 1
      }

      segOffset += seg.getByteLength
      length += seg.length
    }

    // NOTE: need to fix-up start/end offsets of the tree data since text has no start/end except as previous node end and next node start correspondingly
    var j = 0
    while (j < posNeedingAdjustmentIndex) {
      td(posNeedingAdjustment(j) << 1) = lastEndOffset
      j += 1
    }

    new SegmentOffsetTree(td, segmentBytes, si)
  }
}

object SegmentTree {

  final val MAX_VALUE:      Int = Integer.MAX_VALUE >> 2
  final val F_ANCHOR_FLAGS: Int = ~MAX_VALUE

  def getByteOffset(byteOffsetData: Int): Int = {
    val offset = byteOffsetData & MAX_VALUE
    if (offset == MAX_VALUE) -1 else offset
  }

  def getAnchorOffset(byteOffsetData: Int): Int =
    (byteOffsetData & F_ANCHOR_FLAGS) >>> 29

  // ---- Static implementations using raw arrays ----

  def aggrLength(pos: Int, treeData: Array[Int]): Int =
    if (pos < 0) 0 else treeData(pos << 1)

  def byteOffsetData(pos: Int, treeData: Array[Int]): Int =
    treeData((pos << 1) + 1)

  def byteOffset(pos: Int, treeData: Array[Int]): Int =
    getByteOffset(byteOffsetData(pos, treeData))

  def setTreeData(pos: Int, treeData: Array[Int], aggrLen: Int, byteOff: Int, prevAnchorOffset: Int): Unit = {
    assert(byteOff <= MAX_VALUE)
    treeData(pos << 1) = aggrLen
    treeData((pos << 1) + 1) = byteOff | (if (prevAnchorOffset == 0) 0 else prevAnchorOffset << 29)
  }

  def hasPreviousAnchor(pos: Int, treeData: Array[Int]): Boolean =
    getAnchorOffset(treeData((pos << 1) + 1)) > 0

  def previousAnchorOffset(pos: Int, treeData: Array[Int]): Int = {
    val bod = byteOffsetData(pos, treeData)
    getByteOffset(bod) - getAnchorOffset(bod)
  }

  def findSegmentPos(index: Int, treeData: Array[Int], startPos: Int, endPos: Int): Nullable[SegmentTreePos] = boundary {
    // FIX: add segmented sequence stats collection for iteration counts
    // FIX: check first segment and last segment in case it is a scan from start/end of sequence
    if (index == 0 && startPos == 0) {
      break(Nullable(SegmentTreePos(0, 0, 0)))
    }

    var sp         = startPos
    var ep         = endPos
    var iterations = 0
    while (sp < ep) {
      val pos       = (sp + ep) >> 1
      val lastStart = sp
      val lastEnd   = ep

      iterations += 1

      val endIdx = aggrLength(pos, treeData)
      if (index >= endIdx) {
        sp = pos + 1
      } else {
        val startIdx = aggrLength(pos - 1, treeData)
        if (index < startIdx) {
          ep = pos
        } else {
          break(Nullable(SegmentTreePos(pos, startIdx, iterations)))
        }
      }

      assert(
        lastStart != sp || lastEnd != ep,
        "Range and position did not change after iteration: pos=" + pos + ", startPos=" + sp + ", endPos=" + ep
          + "\n" + Arrays.toString(treeData)
      )
    }
    Nullable.empty[SegmentTreePos]
  }

  def findSegment(index: Int, treeData: Array[Int], startPos: Int, endPos: Int, segmentBytes: Array[Byte], baseSeq: BasedSequence): Nullable[Segment] = {
    val treePos = findSegmentPos(index, treeData, startPos, endPos)
    treePos.map { tp =>
      Segment.getSegment(segmentBytes, byteOffset(tp.pos, treeData), tp.pos, tp.startIndex, baseSeq)
    }
  }

  def getSegment(pos: Int, treeData: Array[Int], segmentBytes: Array[Byte], baseSeq: BasedSequence): Segment =
    Segment.getSegment(segmentBytes, byteOffset(pos, treeData), pos, aggrLength(pos, treeData), baseSeq)

  def getPrevAnchor(pos: Int, treeData: Array[Int], segmentBytes: Array[Byte], baseSeq: BasedSequence): Nullable[Segment] = {
    val bod       = byteOffsetData(pos, treeData)
    val anchorOff = getAnchorOffset(bod)
    if (anchorOff > 0) {
      val bo     = getByteOffset(bod) - anchorOff
      val anchor = Segment.getSegment(segmentBytes, bo, -1, 0, baseSeq)
      assert(anchor.isAnchor)
      anchor
    } else {
      Nullable.empty[Segment]
    }
  }

  /** Get char sequence of segment corresponding to sub-sequence in segment tree
    *
    * @param segment
    *   segment
    * @param startIndex
    *   start index of sub-sequence of segment tree
    * @param endIndex
    *   end index of sub-sequence of segment tree
    * @param startPos
    *   start pos of sub-sequence segments in tree
    * @param endPos
    *   end pos of sub-sequence segments in tree
    * @return
    *   subsequence of segment corresponding to part of it which is in the sub-sequence of the tree
    */
  def getCharSequence(segment: Segment, startIndex: Int, endIndex: Int, startPos: Int, endPos: Int): CharSequence = {
    val pos = segment.pos

    if (pos == startPos && pos + 1 == endPos) {
      // need to trim start/end
      segment.getCharSequence.subSequence(startIndex - segment.startIndex, endIndex - segment.startIndex)
    } else if (pos == startPos) {
      // need to trim start
      segment.getCharSequence.subSequence(startIndex - segment.startIndex, segment.length)
    } else if (pos + 1 == endPos) {
      // need to trim end
      segment.getCharSequence.subSequence(0, endIndex - segment.startIndex)
    } else {
      segment.getCharSequence
    }
  }

  // ---- Inner data class ----

  private[tree] class SegmentTreeData(
    val treeData:     Array[Int], // tuples of aggregated length, segment byte offset with flags for prev anchor offset of 1 to 7
    val segmentBytes: Array[Byte], // bytes of serialized segments
    val startIndices: Nullable[Array[Int]] // start index for each segment within the string
  )

  // ---- Build methods ----

  def build(segments: java.lang.Iterable[Seg], allText: CharSequence): SegmentTree = {
    val segmentTreeData = buildTreeData(segments, allText, buildIndexData = true)
    new SegmentTree(segmentTreeData.treeData, segmentTreeData.segmentBytes)
  }

  def build(builder: BasedSegmentBuilder): SegmentTree = {
    val segmentTreeData = buildTreeData(builder.getSegments, builder.getText, buildIndexData = true)
    new SegmentTree(segmentTreeData.treeData, segmentTreeData.segmentBytes)
  }

  /** Build binary tree search data
    *
    * Index data has aggregated lengths with BASE and TEXT segments in the data, Offset data has segment start offset with BASE and ANCHOR segments in the data since TEXT segments have no offset they
    * are skipped
    *
    * The offset data can be used to pass as treeData to findSegmentPos with desired offset instead of index to find a segment which can contain the desired offset, with some post processing logic to
    * handle offset segments which are not in the data
    *
    * @param segments
    *   segments of the tree
    * @param allText
    *   all out of base text
    * @param buildIndexData
    *   true to build index search data, false to build base offset tree data
    * @return
    *   segment tree instance with the data
    */
  def buildTreeData(segments: java.lang.Iterable[Seg], allText: CharSequence, buildIndexData: Boolean): SegmentTreeData = {
    var byteLength    = 0
    var nonAnchors    = 0
    var lastEndOffset = 0

    val iter1 = segments.iterator()
    while (iter1.hasNext) {
      val seg     = iter1.next()
      val segType = Segment.getSegType(seg, allText)
      byteLength += Segment.getSegByteLength(segType, seg.segStart, seg.length)
      if (if (buildIndexData) segType != Segment.SegType.ANCHOR else segType == Segment.SegType.BASE || segType == Segment.SegType.ANCHOR) {
        nonAnchors += 1
      }
      lastEndOffset = seg.end
    }

    val td = new Array[Int](nonAnchors * 2)
    val sb = new Array[Byte](byteLength)
    val si:                   Nullable[Array[Int]] = if (buildIndexData) Nullable.empty else Nullable(new Array[Int](nonAnchors))
    val posNeedingAdjustment: Nullable[Array[Int]] = if (buildIndexData) Nullable.empty else Nullable(new Array[Int](2)) // up to 2 segment adjustments
    var posNeedingAdjustmentIndex = 0

    var prevAnchorOffset = -1

    var pos     = 0
    var offset  = 0
    var aggrLen = 0

    val iter2 = segments.iterator()
    while (iter2.hasNext) {
      val seg       = iter2.next()
      val segOffset = offset

      offset = Segment.addSegBytes(sb, offset, seg, allText)
      val segType = Segment.SegType.fromTypeMask(sb(segOffset))

      if (buildIndexData) {
        if (segType == Segment.SegType.ANCHOR) {
          prevAnchorOffset = segOffset
        } else {
          aggrLen += seg.length
          setTreeData(pos, td, aggrLen, segOffset, if (prevAnchorOffset == -1) 0 else segOffset - prevAnchorOffset)
          pos += 1
          prevAnchorOffset = -1
        }
      } else {
        si.get(pos) = aggrLen

        if (posNeedingAdjustmentIndex > 0 && seg.start >= 0) {
          // set it to the correct value
          val iMax = posNeedingAdjustmentIndex
          var j    = 0
          while (j < iMax) {
            td(posNeedingAdjustment.get(j) << 1) = seg.start
            j += 1
          }
          posNeedingAdjustmentIndex = 0
        }

        aggrLen += seg.length

        if (segType == Segment.SegType.BASE || segType == Segment.SegType.ANCHOR) {
          // the use of getEnd() here is temporary for all but the last base segment, it will be overwritten by getStart() by next segment
          setTreeData(pos, td, seg.end, segOffset, 0)
          posNeedingAdjustment.get(posNeedingAdjustmentIndex) = pos
          posNeedingAdjustmentIndex += 1
          pos += 1
        }
      }
    }

    // NOTE: need to fix-up start/end offsets of the tree data since text has no start/end except as previous node end and next node start correspondingly
    if (!buildIndexData) {
      var j = 0
      while (j < posNeedingAdjustmentIndex) {
        td(posNeedingAdjustment.get(j) << 1) = lastEndOffset
        j += 1
      }
    }

    new SegmentTreeData(td, sb, si)
  }
}
