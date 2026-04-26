/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/BasedOffsetTracker.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/BasedOffsetTracker.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package builder
package tree

import ssg.md.Nullable
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class BasedOffsetTracker protected (
  val sequence:          BasedSequence, // sequence on which this tracker is based, not the base sequence of original sequence
  val segmentOffsetTree: SegmentOffsetTree
) {

  private var lastSegment: Nullable[Segment] = Nullable.empty

  protected def this(sequence: BasedSequence, segmentTree: SegmentTree) =
    this(sequence, segmentTree.getSegmentOffsetTree(sequence.getBaseSequence))

  def size: Int = segmentOffsetTree.size

  /** Return the range of indices in the sequence of this based offset tracker that correspond to the given offset in the base sequence from which this sequence was derived.
    *
    * NOTE: intended use is to recover the editing caret position from original text after some text transformation such as formatting, rendering HTML or paragraph wrapping.
    *
    * @param offset
    *   offset in base sequence
    * @param isEndOffset
    *   if true then offset represents the range [offset, offset) so it is located between character at offset-1 and character at offset if false then offset represents the character at offset and the
    *   range [offset, offset+1)
    * @return
    *   information about the offset in this sequence
    */
  def getOffsetInfo(offset: Int, isEndOffset: Boolean): OffsetInfo = {
    // if is end offset then will not
    val offsetEnd = if (isEndOffset) offset else offset + 1

    // if offsetEnd <= firstSegment.startOffset then indexRange is [0,0)
    // if offset >= lastSegment.endOffset then indexRange is [sequence.length, sequence.length)

    // otherwise, find segment for the offset in the segmentOffsetTree:

    // if offsetEnd > segment.startOffset && offset < segment.endOffset then
    //      indexRange.start = segment.startIndex + offset - segment.startOffset, indexRange.length = offsetEnd-offset

    // if offsetEnd == segment.startOffset
    //      indexRange is preceding TEXT segment indexRange or if none then [segment.startIndex, segment.startIndex)

    // if offset == segment.endOffset
    //      indexRange is preceding TEXT segment indexRange or if none then [segment.startIndex, segment.startIndex)

    if (offsetEnd <= sequence.startOffset) {
      // before sequence
      new OffsetInfo(-1, offset, true, 0)
    } else if (offset >= sequence.endOffset) {
      // after sequence
      new OffsetInfo(segmentOffsetTree.size, offset, true, sequence.length)
    } else {
      val seg = segmentOffsetTree.findSegmentByOffset(offset, sequence.getBaseSequence, lastSegment)
      seg.fold {
        // outside the sequence
        if (offset < segmentOffsetTree.getSegment(0, sequence).getStartOffset) {
          new OffsetInfo(-1, offset, true, 0)
        } else {
          if (offset < segmentOffsetTree.getSegment(segmentOffsetTree.size - 1, sequence).getEndOffset) {
            // RELEASE: remove exception
            throw new IllegalStateException("Unexpected")
          }
          new OffsetInfo(segmentOffsetTree.size, offset, true, sequence.length)
        }
      } { s =>
        lastSegment = s

        if (offsetEnd > s.getStartOffset && offset < s.getEndOffset) {
          // inside base segment
          val si = s.startIndex + offset - s.getStartOffset
          val ei = s.startIndex + offsetEnd - s.getStartOffset
          new OffsetInfo(s.pos, offset, isEndOffset, si, ei)
        } else if (offsetEnd <= s.getStartOffset) {
          val textSegment = segmentOffsetTree.getPreviousText(s, sequence)
          val (si, ei)    = textSegment.fold((s.startIndex, s.startIndex)) { ts =>
            (ts.startIndex, ts.endIndex)
          }
          new OffsetInfo(s.pos - 1, offset, true, si, ei)
        } else if (offset >= s.getEndOffset) {
          val textSegment = segmentOffsetTree.getNextText(s, sequence)
          val (si, ei)    = textSegment.fold((s.endIndex, s.endIndex)) { ts =>
            (ts.startIndex, ts.endIndex)
          }
          new OffsetInfo(s.pos + 1, offset, true, si, ei)
        } else {
          throw new IllegalStateException(
            String.format(
              "Unexpected offset: [%d, %d), seg: %s, not inside nor at start nor at end",
              offset:    Integer,
              offsetEnd: Integer,
              s.toString
            )
          )
        }
      }
    }
  }

  override def toString: String =
    "BasedOffsetTracker{" +
      "tree=" + segmentOffsetTree +
      "}"
}

object BasedOffsetTracker {

  /** Create a based offset tracker for the given sequence
    *
    * @param sequence
    *   sequence which to create offset tracker
    * @return
    *   based offset tracker
    */
  def create(sequence: BasedSequence): BasedOffsetTracker = {
    val segmentTree = sequence.getSegmentTree
    new BasedOffsetTracker(sequence, segmentTree)
  }

  /** Create a based offset tracker for the given sequence
    *
    * @param sequence
    *   sequence which to create offset tracker
    * @param segmentOffsetTree
    *   segment offset tree for the sequence
    * @return
    *   based offset tracker
    */
  def create(sequence: BasedSequence, segmentOffsetTree: SegmentOffsetTree): BasedOffsetTracker =
    new BasedOffsetTracker(sequence, segmentOffsetTree)
}
