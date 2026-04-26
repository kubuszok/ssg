/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/BasedSegmentBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/BasedSegmentBuilder.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.util.sequence.{ BasedSequence, PositionAnchor, Range }

class BasedSegmentBuilder private (
  val baseSeq:   BasedSequence,
  val optimizer: SegmentOptimizer,
  options:       Int
) extends SegmentBuilderBase[BasedSegmentBuilder](options),
      IBasedSegmentBuilder[BasedSegmentBuilder] {

  private def this(baseSeq: BasedSequence) =
    this(baseSeq.getBaseSequence, new CharRecoveryOptimizer(PositionAnchor.CURRENT), ISegmentBuilder.F_INCLUDE_ANCHORS)

  private def this(baseSeq: BasedSequence, optimizer: SegmentOptimizer) =
    this(baseSeq.getBaseSequence, optimizer, ISegmentBuilder.F_INCLUDE_ANCHORS)

  private def this(baseSeq: BasedSequence, options: Int) =
    this(baseSeq.getBaseSequence, new CharRecoveryOptimizer(PositionAnchor.CURRENT), options)

  override def baseSequence: BasedSequence = baseSeq

  override protected def optimizeText(parts: Array[Object]): Array[Object] =
    optimizer.apply(baseSeq, parts)

  override protected def handleOverlap(parts: Array[Object]): Array[Object] = {
    // convert overlap to text from our base
    // range overlaps with last segment in the list
    val lastSeg = parts(0).asInstanceOf[Range]
    val text    = parts(1).asInstanceOf[CharSequence]
    val range   = parts(2).asInstanceOf[Range]
    assert(!lastSeg.isNull && lastSeg.end > range.start)

    var overlap: Range = Range.NULL
    var after:   Range = Range.NULL

    if (range.end <= lastSeg.start) {
      // the whole thing is before
      overlap = range
    } else if (range.start <= lastSeg.start) {
      // part before, maybe some after
      overlap = Range.of(range.start, Math.min(range.end, lastSeg.end))
      if (lastSeg.end < range.end) {
        after = Range.of(lastSeg.end, range.end)
      }
    } else if (range.end <= lastSeg.end) {
      // all contained within
      overlap = range
    } else {
      assert(range.start < lastSeg.end)
      overlap = range.withEnd(lastSeg.end)
      after = range.withStart(lastSeg.end)
    }

    val overlapSpan = overlap.span
    assert(overlapSpan + after.span == range.span)

    // append overlap to text
    if (text.length() == 0) {
      parts(1) = baseSeq.subSequence(overlap.start, overlap.end).toString.asInstanceOf[Object]
    } else {
      parts(1) = (text.toString + baseSeq.subSequence(overlap.start, overlap.end).toString).asInstanceOf[Object]
    }
    parts(2) = after

    parts
  }

  override def toStringWithRangesVisibleWhitespace(): String =
    super.toStringWithRangesVisibleWhitespace(baseSeq)

  override def toStringWithRanges(): String =
    super.toStringWithRanges(baseSeq)

  override def toStringChars(): String =
    super.toString(baseSeq)
}

object BasedSegmentBuilder {

  def emptyBuilder(sequence: BasedSequence): BasedSegmentBuilder =
    new BasedSegmentBuilder(sequence)

  def emptyBuilder(sequence: BasedSequence, options: Int): BasedSegmentBuilder =
    new BasedSegmentBuilder(sequence, options)

  def emptyBuilder(sequence: BasedSequence, optimizer: SegmentOptimizer): BasedSegmentBuilder =
    new BasedSegmentBuilder(sequence, optimizer)

  def emptyBuilder(sequence: BasedSequence, optimizer: SegmentOptimizer, options: Int): BasedSegmentBuilder =
    new BasedSegmentBuilder(sequence.getBaseSequence, optimizer, options)
}
