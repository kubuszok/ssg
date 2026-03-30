/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/SequenceBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.Nullable
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.{ BasedOptionsHolder, BasedSequence, Range, SegmentedSequence, SequenceUtils }

import java.util as ju

/** A Builder for Segmented BasedSequences
  */
class SequenceBuilder private (
  private val altBase:         BasedSequence,
  private val baseSeq:         BasedSequence,
  private val segments:        BasedSegmentBuilder,
  private val equivalentBases: ju.HashMap[BasedSequence, java.lang.Boolean]
) extends ISequenceBuilder[SequenceBuilder, BasedSequence] {

  private var resultSeq: Nullable[BasedSequence] = Nullable.empty[BasedSequence]

  /** Construct a base sequence builder for given base sequence with default options.
    *
    * NOTE: the builder is always constructed for the base sequence of the base. ie. for the based sequence returned by [[BasedSequence.getBaseSequence]], so any subsequence from a base can be used as
    * argument for the constructor
    */
  private def this(base: BasedSequence, optimizer: Nullable[SegmentOptimizer]) = {
    this(
      base,
      base.getBaseSequence, {
        val bs      = base.getBaseSequence
        var options = ISegmentBuilder.F_DEFAULT
        // NOTE: if full segmented is not specified, then collect first256 stats for use by tree impl
        if (!bs.anyOptions(BasedOptionsHolder.F_FULL_SEGMENTED_SEQUENCES) || bs.anyOptions(BasedOptionsHolder.F_COLLECT_FIRST256_STATS)) {
          options |= ISegmentBuilder.F_TRACK_FIRST256
        }
        if (bs.anyOptions(BasedOptionsHolder.F_NO_ANCHORS)) {
          options &= ~ISegmentBuilder.F_INCLUDE_ANCHORS
        }
        if (optimizer.isDefined) BasedSegmentBuilder.emptyBuilder(bs, optimizer.get, options)
        else BasedSegmentBuilder.emptyBuilder(bs, options)
      },
      new ju.HashMap[BasedSequence, java.lang.Boolean]()
    )
  }

  private def this(base: BasedSequence, options: Int, optimizer: Nullable[SegmentOptimizer], equivalentBases: ju.HashMap[BasedSequence, java.lang.Boolean]) = {
    this(
      base,
      base.getBaseSequence, {
        val bs   = base.getBaseSequence
        var opts = options
        if (!bs.anyOptions(BasedOptionsHolder.F_FULL_SEGMENTED_SEQUENCES) || bs.anyOptions(BasedOptionsHolder.F_COLLECT_FIRST256_STATS)) {
          opts |= ISegmentBuilder.F_TRACK_FIRST256
        }
        if (bs.anyOptions(BasedOptionsHolder.F_NO_ANCHORS)) {
          opts &= ~ISegmentBuilder.F_INCLUDE_ANCHORS
        }
        if (optimizer.isDefined) BasedSegmentBuilder.emptyBuilder(bs, optimizer.get, opts)
        else BasedSegmentBuilder.emptyBuilder(bs, opts)
      },
      equivalentBases
    )
  }

  def baseSequence: BasedSequence = baseSeq

  def segmentBuilder: BasedSegmentBuilder = segments

  def getLastRangeOrNull: Nullable[Range] = {
    val part = segments.getPart(segments.size)
    part match {
      case range: Range if range.isNotNull => Nullable(range)
      case _ => Nullable.empty[Range]
    }
  }

  override def singleBasedSequence: Nullable[BasedSequence] = {
    val range = segments.getBaseSubSequenceRange
    if (range.isDefined) Nullable(baseSeq.subSequence(range.get.start, range.get.end))
    else Nullable.empty[BasedSequence]
  }

  override def getBuilder: SequenceBuilder =
    new SequenceBuilder(altBase, segments.options, Nullable(segments.optimizer), equivalentBases)

  override def charAt(index: Int): Char = toSequence.charAt(index)

  private def isCommonBaseSequence(chars: BasedSequence): Boolean =
    if (chars.isNull) false
    else {
      val charsBaseSequence = chars.getBaseSequence
      if (charsBaseSequence eq baseSeq) true
      else {
        // see if it is known to be equivalent or not equivalent
        val inCommon = equivalentBases.get(charsBaseSequence)
        if (inCommon != null) inCommon.booleanValue() // @nowarn — Map.get returns null at Java interop boundary
        else {
          val equivalent = baseSeq.equals(charsBaseSequence)
          equivalentBases.put(charsBaseSequence, java.lang.Boolean.valueOf(equivalent))
          equivalent
        }
      }
    }

  override def append(chars: Nullable[CharSequence], startIndex: Int, endIndex: Int): SequenceBuilder = {
    if (chars.isDefined && chars.get.isInstanceOf[BasedSequence] && isCommonBaseSequence(chars.get.asInstanceOf[BasedSequence])) {
      val bs = chars.get.asInstanceOf[BasedSequence]
      if (bs.isNotNull) {
        if (startIndex == 0 && endIndex == chars.get.length()) {
          bs.addSegments(segments)
        } else {
          bs.subSequence(startIndex, endIndex).addSegments(segments)
        }
        resultSeq = Nullable.empty[BasedSequence]
      }
    } else if (chars.isDefined && startIndex < endIndex) {
      if (startIndex == 0 && endIndex == chars.get.length()) {
        segments.append(chars.get)
      } else {
        segments.append(chars.get.subSequence(startIndex, endIndex))
      }
      resultSeq = Nullable.empty[BasedSequence]
    }
    this
  }

  override def append(c: Char): SequenceBuilder = {
    segments.append(c)
    resultSeq = Nullable.empty[BasedSequence]
    this
  }

  override def append(c: Char, count: Int): SequenceBuilder = {
    if (count > 0) {
      segments.append(c, count)
      resultSeq = Nullable.empty[BasedSequence]
    }
    this
  }

  def append(startOffset: Int, endOffset: Int): SequenceBuilder =
    addByOffsets(startOffset, endOffset)

  def append(chars: Range): SequenceBuilder = addRange(chars)

  def addRange(range: Range): SequenceBuilder = {
    segments.append(range)
    resultSeq = Nullable.empty[BasedSequence]
    this
  }

  def addByOffsets(startOffset: Int, endOffset: Int): SequenceBuilder = {
    if (startOffset < 0 || startOffset > endOffset || endOffset > baseSeq.length()) {
      throw new IllegalArgumentException(s"addByOffsets start/end must be a valid range in [0, ${baseSeq.length()}], got: [$startOffset, $endOffset]")
    }
    segments.append(Range.of(startOffset, endOffset))
    resultSeq = Nullable.empty[BasedSequence]
    this
  }

  def addByLength(startOffset: Int, textLength: Int): SequenceBuilder =
    add(Nullable(baseSeq.subSequence(startOffset, startOffset + textLength): CharSequence))

  override def toSequence: BasedSequence = {
    if (!resultSeq.isDefined) {
      resultSeq = Nullable(SegmentedSequence.create(this))
    }
    resultSeq.get
  }

  /** Construct sequence from this builder using another based sequence which is character identical to this builder's baseSeq
    */
  def toSequence(altSequence: BasedSequence): BasedSequence =
    toSequence(altSequence, Nullable.empty[CharPredicate], Nullable.empty[CharPredicate])

  /** Construct sequence from this builder using another based sequence which is character identical to this builder's baseSeq
    */
  def toSequence(altSequence: BasedSequence, trimStart: Nullable[CharPredicate], ignoreCharDiff: Nullable[CharPredicate]): BasedSequence =
    if (altSequence eq altBase) {
      toSequence
    } else {
      assert(
        altSequence.equals(altBase),
        s"altSequence must be character identical to builder.altBase\naltBase: '${altBase.toVisibleWhitespaceString()}'\n altSeq: '${altSequence.toVisibleWhitespaceString()}'\n"
      )

      // this is an identical but different base sequence, need to map to it. Ranges are indices into altSequence and must be converted to offsets.
      val altBuilder = new SequenceBuilder(altSequence, segments.options, Nullable(segments.optimizer), new ju.HashMap[BasedSequence, java.lang.Boolean]())

      var deleted = 0
      val iter    = segments.iterator()
      while (iter.hasNext) {
        val part = iter.next()
        part match {
          case range: Range =>
            var s            = altSequence.subSequence(deleted + range.start, deleted + range.end)
            val startTrimmed = if (trimStart.isDefined) s.countLeading(trimStart.get) else 0

            if (startTrimmed > 0) {
              deleted += startTrimmed
              s = altSequence.subSequence(deleted + range.start, deleted + range.end)
              // NOTE: here there could be differences in space vs tab vs EOL due to shift and wrapping
            }
            altBuilder.append(Nullable(s: CharSequence))
          case seq: CharSequence =>
            altBuilder.append(Nullable(seq))
          case other if other != null => // @nowarn — null check at iteration boundary
            throw new IllegalStateException("Invalid part type " + other.getClass)
          case _ => // null — skip
        }
      }

      val result   = SegmentedSequence.create(altBuilder)
      val sequence = toSequence
      assert(
        SequenceUtils.compare(Nullable(result: CharSequence), Nullable(sequence: CharSequence), false, ignoreCharDiff) == 0,
        s"result must be character identical to builder.toSequence()\nresult: '${result.toVisibleWhitespaceString()}'\n sequence: '${sequence.toVisibleWhitespaceString()}'\n"
      )
      result
    }

  /** Construct sequence from this builder using another based sequence which is character identical to this builder's baseSeq by length
    */
  def toSequenceByIndex(altSequence: BasedSequence, trimStart: Nullable[CharPredicate], ignoreCharDiff: Nullable[CharPredicate]): BasedSequence =
    if (altSequence eq altBase) {
      toSequence
    } else {
      assert(
        altSequence.equals(altBase),
        s"altSequence must be character identical to builder.altBase\naltBase: '${altBase.toVisibleWhitespaceString()}'\n altSeq: '${altSequence.toVisibleWhitespaceString()}'\n"
      )

      val altBuilder = new SequenceBuilder(altSequence, segments.options, Nullable(segments.optimizer), new ju.HashMap[BasedSequence, java.lang.Boolean]())

      var length  = 0
      var deleted = 0
      val iter    = segments.iterator()
      while (iter.hasNext) {
        val part = iter.next()
        part match {
          case range: Range =>
            var s            = altSequence.subSequence(length + deleted, length + deleted + range.span)
            val startTrimmed = if (trimStart.isDefined) s.countLeading(trimStart.get) else 0

            if (startTrimmed > 0) {
              // NOTE: here there could be differences in space vs tab vs EOL due to shift and remapping
              deleted += startTrimmed
              s = altSequence.subSequence(length + deleted, length + deleted + range.span)
            }

            altBuilder.append(Nullable(s: CharSequence))
            length += range.span
          case seq: CharSequence =>
            altBuilder.append(Nullable(seq))
            length += seq.length()
          case other if other != null => // @nowarn — null check at iteration boundary
            throw new IllegalStateException("Invalid part type " + other.getClass)
          case _ => // null — skip
        }
      }

      val result   = SegmentedSequence.create(altBuilder)
      val sequence = toSequence

      assert(
        SequenceUtils.compare(Nullable(result: CharSequence), Nullable(sequence: CharSequence), false, ignoreCharDiff) == 0,
        s"result must be character identical to builder.toSequence()\nresult: '${result.toVisibleWhitespaceString()}'\n sequence: '${sequence.toVisibleWhitespaceString()}'\n"
      )
      result
    }

  override def length: Int = segments.length

  def toStringWithRanges: String = segments.toStringWithRangesVisibleWhitespace(baseSeq)

  def toStringWithRanges(toVisibleWhiteSpace: Boolean): String =
    if (toVisibleWhiteSpace) segments.toStringWithRangesVisibleWhitespace(baseSeq)
    else segments.toStringWithRanges(baseSeq)

  override def toString: String = {
    val sb   = new StringBuilder()
    val iter = segments.iterator()
    while (iter.hasNext) {
      val part = iter.next()
      part match {
        case range: Range =>
          val s = baseSeq.subSequence(range.start, range.end)
          if (s.isNotEmpty()) {
            s.appendTo(sb)
          }
        case seq: CharSequence =>
          sb.append(seq)
        case other if other != null => // @nowarn — null check at iteration boundary
          throw new IllegalStateException("Invalid part type " + other.getClass)
        case _ => // null — skip
      }
    }
    sb.toString
  }

  def toStringNoAddedSpaces: String = {
    val sb   = new StringBuilder()
    val iter = segments.iterator()
    while (iter.hasNext) {
      val part = iter.next()
      part match {
        case range: Range =>
          sb.append(baseSeq.subSequence(range.start, range.end))
        case seq: CharSequence =>
          sb.append(seq)
        case other if other != null => // @nowarn — null check at iteration boundary
          throw new IllegalStateException("Invalid part type " + other.getClass)
        case _ => // null — skip
      }
    }
    sb.toString
  }
}

object SequenceBuilder {

  def emptyBuilder(base: BasedSequence): SequenceBuilder =
    new SequenceBuilder(base, Nullable.empty[SegmentOptimizer])

  def emptyBuilder(base: BasedSequence, optimizer: SegmentOptimizer): SequenceBuilder =
    new SequenceBuilder(base, Nullable(optimizer))

  def emptyBuilder(base: BasedSequence, options: Int): SequenceBuilder =
    new SequenceBuilder(base, options, Nullable.empty[SegmentOptimizer], new ju.HashMap[BasedSequence, java.lang.Boolean]())

  def emptyBuilder(base: BasedSequence, options: Int, optimizer: SegmentOptimizer): SequenceBuilder =
    new SequenceBuilder(base, options, Nullable(optimizer), new ju.HashMap[BasedSequence, java.lang.Boolean]())
}
