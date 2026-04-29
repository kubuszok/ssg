/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SegmentedSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SegmentedSequence.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.data.{ DataHolder, DataKeyBase }
import ssg.md.util.sequence.builder.SequenceBuilder

import scala.jdk.CollectionConverters.*

/** A BasedSequence which consists of segments of other BasedSequences
  */
abstract class SegmentedSequence protected (
  protected val baseSeq:      BasedSequence,
  protected var _startOffset: Int,
  protected var _endOffset:   Int,
  protected val _length:      Int
) extends BasedSequenceImpl(0),
      ReplacedBasedSequence {

  // NOTE: segmented sequence with no based segments in it gets both offsets as -1
  if (_startOffset < 0 && _endOffset < 0) {
    _startOffset = 0
    _endOffset = 0
  }

  assert(_startOffset >= 0, s"startOffset: ${_startOffset}")
  assert(_endOffset >= _startOffset && _endOffset <= baseSeq.length(), s"endOffset: ${_endOffset}")
  assert(baseSeq eq baseSeq.getBaseSequence)

  final override def getBase: AnyRef = baseSeq.getBase

  final override def getBaseSequence: BasedSequence = baseSeq

  /** Get the start in the base sequence for this segmented sequence.
    *
    * NOTE: this is the startOffset determined when the sequence was built from segments and may differ from the startOffset of the first based segment in this sequence
    *
    * @return
    *   start in base sequence
    */
  final override def startOffset: Int = _startOffset

  /** Get the end offset in the base sequence
    *
    * NOTE: this is the endOffset determined when the sequence was built from segments and may differ from the endOffset of the last based segment in this sequence
    *
    * @return
    *   end in base sequence
    */
  final override def endOffset: Int = _endOffset

  final override def optionFlags: Int = getBaseSequence.optionFlags

  final override def allOptions(options: Int): Boolean = getBaseSequence.allOptions(options)

  final override def anyOptions(options: Int): Boolean = getBaseSequence.anyOptions(options)

  final override def getOption[T](dataKey: DataKeyBase[T]): Nullable[T] = getBaseSequence.getOption(dataKey)

  final override def options: Nullable[DataHolder] = getBaseSequence.options

  final override def length(): Int = _length

  final override def getSourceRange: Range = Range.of(startOffset, endOffset)

  final override def baseSubSequence(startIndex: Int, endIndex: Int): BasedSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, baseSeq.length())
    baseSeq.baseSubSequence(startIndex, endIndex)
  }
}

object SegmentedSequence {

  /** Use [[BasedSequence.getBuilder]] and then [[SequenceBuilder.addAll]] or if you know which are based segments vs. out of base Strings then use [[BasedSegmentBuilder]] to construct segments
    * directly.
    *
    * Use only if you absolutely need to use this old method because it calls the builder.addAll() for all the segments anyway.
    *
    * @param basedSequence
    *   base sequence for the segments
    * @param segments
    *   list of based sequences to put into a based sequence
    * @return
    *   based sequence of segments
    */
  def create(basedSequence: BasedSequence, segments: java.lang.Iterable[? <: BasedSequence]): BasedSequence =
    create(SequenceBuilder.emptyBuilder(basedSequence).addAll(segments.asScala))

  def create(segments: BasedSequence*): BasedSequence =
    if (segments.isEmpty) BasedSequence.NULL
    else create(segments.head, segments.asJava)

  def create(builder: SequenceBuilder): BasedSequence = {
    val baseSubSequence = builder.singleBasedSequence
    if (baseSubSequence.isDefined) {
      baseSubSequence.get
    } else if (!builder.isEmpty) {
      val baseSequence = builder.baseSequence
      if (baseSequence.anyOptions(BasedOptionsHolder.F_FULL_SEGMENTED_SEQUENCES)) {
        SegmentedSequenceFull.create(baseSequence, builder.segmentBuilder)
      } else if (baseSequence.anyOptions(BasedOptionsHolder.F_TREE_SEGMENTED_SEQUENCES)) {
        SegmentedSequenceTree.create(baseSequence, builder.segmentBuilder)
      } else {
        // Can decide based on segments and length but tree based is not slower and much more efficient
        SegmentedSequenceTree.create(baseSequence, builder.segmentBuilder)
      }
    } else {
      BasedSequence.NULL
    }
  }

  @deprecated(
    "Use BasedSequence.getBuilder and then SequenceBuilder.addAll or use SegmentedSequence.create(BasedSequence, Iterable)",
    ""
  )
  def of(basedSequence: BasedSequence, segments: java.lang.Iterable[? <: BasedSequence]): BasedSequence =
    create(basedSequence, segments)

  @deprecated("Use SegmentedSequence.create", "")
  def of(segments: BasedSequence*): BasedSequence =
    create(segments*)
}
