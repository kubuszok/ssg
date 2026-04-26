/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SegmentedSequenceFull.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SegmentedSequenceFull.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence

import ssg.md.util.sequence.builder.{ IBasedSegmentBuilder, ISegmentBuilder }

import scala.util.boundary
import scala.util.boundary.break

/** A BasedSequence which consists of segments of other BasedSequences NOTE: very efficient for random access but extremely wasteful with space by allocating 4 bytes per character in the sequence with
  * corresponding construction penalty use SegmentedSequenceTree which is binary tree based segmented sequence with minimal overhead and optimized to give penalty free random access for most
  * applications.
  */
final class SegmentedSequenceFull private (
  baseSeq:                     BasedSequence,
  startOffset:                 Int,
  endOffset:                   Int,
  length:                      Int,
  private val nonBaseChars:    Boolean,
  private val baseOffsets:     Array[Int],
  private val baseStartOffset: Int
) extends SegmentedSequence(baseSeq, startOffset, endOffset, length) {

  override def getIndexOffset(index: Int): Int = {
    SequenceUtils.validateIndexInclusiveEnd(index, length)
    val offset = baseOffsets(baseStartOffset + index)
    if (offset < 0) -1 else offset
  }

  override def addSegments(builder: IBasedSegmentBuilder[?]): Unit =
    // FIX: clean up and optimize the structure. it is error prone and inefficient
    BasedUtils.generateSegments(builder, this)

  override def charAt(index: Int): Char = {
    SequenceUtils.validateIndex(index, length)
    val offset = baseOffsets(baseStartOffset + index)
    if (offset < 0) {
      /* HACK: allows having characters which are not from original base sequence
           but with the only penalty for charAt access being an extra indirection,
           which is a small price to pay for having the flexibility of adding out of
           context text to the based sequence.
       */
      (-offset - 1).toChar
    } else {
      this.baseSeq.charAt(offset)
    }
  }

  override def subSequence(startIndex: Int, endIndex: Int): BasedSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, length)
    if (startIndex == 0 && endIndex == _length) this
    else makeSubSequence(baseSeq, baseOffsets, baseStartOffset + startIndex, nonBaseChars, endIndex - startIndex)
  }

  private def makeSubSequence(
    baseSeq:         BasedSequence,
    baseOffsets:     Array[Int],
    baseStartOffset: Int,
    nonBaseChars:    Boolean,
    length:          Int
  ): SegmentedSequenceFull = {
    val iMax = baseOffsets.length - 1
    assert(baseStartOffset + length <= iMax, "Sub-sequence offsets list length < baseStartOffset + sub-sequence length")

    var startOff = 0
    var endOff   = 0

    if (!nonBaseChars) {
      if (baseStartOffset < iMax) {
        startOff = baseOffsets(baseStartOffset)
      } else {
        startOff = baseSeq.endOffset
      }

      if (length == 0) {
        endOff = startOff
      } else {
        endOff = baseOffsets(baseStartOffset + length - 1) + 1
        assert(startOff <= endOff)
      }
    } else {
      // start is the first real start in this sequence or after it in the parent
      boundary {
        var iS = baseStartOffset
        while (iS < iMax) {
          if (baseOffsets(iS) >= 0) {
            startOff = baseOffsets(iS)

            if (length != 0) {
              // end is the last real offset + 1 in this sequence up to the start index where startOffset was found
              var iE       = baseStartOffset + length
              var foundEnd = false
              while (iE > iS && !foundEnd) {
                iE -= 1
                if (baseOffsets(iE) >= 0) {
                  endOff = baseOffsets(iE) + 1
                  assert(startOff <= endOff)
                  foundEnd = true
                }
              }
              if (!foundEnd) {
                endOff = startOff
              }
            } else {
              endOff = startOff
            }
            break(())
          }
          iS += 1
        }

        // if no real start after then it is the base's end since we had no real start after, these chars and after are all out of base chars
        startOff = baseSeq.endOffset
        endOff = startOff
      }
    }

    new SegmentedSequenceFull(
      baseSeq,
      startOff,
      endOff,
      length,
      nonBaseChars,
      baseOffsets,
      baseStartOffset
    )
  }
}

object SegmentedSequenceFull {

  /** Base Constructor
    *
    * @param baseSequence
    *   base sequence for segmented sequence
    * @param builder
    *   builder for which to construct segmented sequence
    * @return
    *   segmented sequence
    */
  def create(baseSequence: BasedSequence, builder: ISegmentBuilder[?]): SegmentedSequenceFull = {
    val baseSeq         = baseSequence.getBaseSequence
    val length          = builder.length
    val baseStartOffset = 0
    val baseOffsets     = new Array[Int](length + 1)

    var index = 0
    val iter  = builder.iterator()
    while (iter.hasNext) {
      val part = iter.next()
      part match {
        case range: Range =>
          if (!range.isEmpty) {
            var i    = range.start
            val iMax = range.end
            while (i < iMax) {
              baseOffsets(index) = i
              index += 1
              i += 1
            }
          }
        case seq: CharSequence =>
          var i    = 0
          val iMax = seq.length()
          while (i < iMax) {
            baseOffsets(index) = -seq.charAt(i) - 1
            index += 1
            i += 1
          }
        case other if other != null => // @nowarn — null check at iteration boundary
          throw new IllegalStateException("Invalid part type " + other.getClass)
        case _ => // null — skip
      }
    }

    val end = baseOffsets(length - 1)
    baseOffsets(length) = if (end < 0) end - 1 else end + 1

    val startOff     = builder.startOffset
    val endOff       = builder.endOffset
    val nonBaseChars = builder.textLength > 0

    if (baseSeq.anyOptions(BasedOptionsHolder.F_COLLECT_SEGMENTED_STATS)) {
      val stats = baseSeq.getOption(BasedOptionsHolder.SEGMENTED_STATS)
      if (stats.isDefined) {
        stats.get.addStats(builder.noAnchorsSize, length, baseOffsets.length * 4)
      }
    }

    new SegmentedSequenceFull(
      baseSeq,
      startOff,
      endOff,
      length,
      nonBaseChars,
      baseOffsets,
      baseStartOffset
    )
  }

  @deprecated("Use BasedSequence.getBuilder and SequenceBuilder.addAll or SegmentedSequence.create", "")
  def of(basedSequence: BasedSequence, segments: java.lang.Iterable[? <: BasedSequence]): BasedSequence =
    SegmentedSequence.create(basedSequence, segments)

  @deprecated("Use SegmentedSequence.create", "")
  def of(segments: BasedSequence*): BasedSequence =
    SegmentedSequence.create(segments*)
}
