/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/MappedBasedSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/MappedBasedSequence.java
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
import ssg.md.util.sequence.builder.IBasedSegmentBuilder
import ssg.md.util.sequence.mappers.CharMapper

/** A BasedSequence which maps characters according to CharMapper
  */
final class MappedBasedSequence private (
  private val baseSeq: BasedSequence,
  private val mapper:  CharMapper
) extends BasedSequenceImpl(0),
      MappedSequence[BasedSequence],
      ReplacedBasedSequence {

  override def charMapper: CharMapper = mapper

  override def charAt(index: Int): Char = mapper.map(baseSeq.charAt(index))

  override def charSequence: BasedSequence = baseSeq

  override def length(): Int = baseSeq.length()

  override def toMapped(mapper: CharMapper): BasedSequence =
    if (mapper eq CharMapper.IDENTITY) this
    else new MappedBasedSequence(baseSeq, this.mapper.andThen(mapper))

  override def optionFlags: Int = getBaseSequence.optionFlags

  override def allOptions(options: Int): Boolean = getBaseSequence.allOptions(options)

  override def anyOptions(options: Int): Boolean = getBaseSequence.anyOptions(options)

  override def getOption[T](dataKey: DataKeyBase[T]): Nullable[T] = getBaseSequence.getOption(dataKey)

  override def options: Nullable[DataHolder] = getBaseSequence.options

  override def sequenceOf(baseSeq: Nullable[CharSequence], startIndex: Int, endIndex: Int): BasedSequence =
    if (baseSeq.isDefined && baseSeq.get.isInstanceOf[MappedBasedSequence]) {
      val bs = baseSeq.get.asInstanceOf[BasedSequence]
      if (startIndex == 0 && endIndex == baseSeq.get.length()) bs
      else bs.subSequence(startIndex, endIndex).toMapped(mapper)
    } else {
      new MappedBasedSequence(this.baseSeq.sequenceOf(baseSeq, startIndex, endIndex), mapper)
    }

  override def subSequence(startIndex: Int, endIndex: Int): BasedSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, length())
    if (startIndex == 0 && endIndex == baseSeq.length()) this
    else new MappedBasedSequence(baseSeq.subSequence(startIndex, endIndex), mapper)
  }

  override def getBase: AnyRef = baseSeq.getBase

  override def getBaseSequence: BasedSequence = baseSeq.getBaseSequence

  override def startOffset: Int = baseSeq.startOffset

  override def endOffset: Int = baseSeq.endOffset

  override def getIndexOffset(index: Int): Int =
    if (baseSeq.charAt(index) == charAt(index)) baseSeq.getIndexOffset(index) else -1

  override def addSegments(builder: IBasedSegmentBuilder[?]): Unit =
    BasedUtils.generateSegments(builder, this)

  override def getSourceRange: Range = baseSeq.getSourceRange
}

object MappedBasedSequence {

  def mappedOf(baseSeq: BasedSequence, mapper: CharMapper): BasedSequence =
    new MappedBasedSequence(baseSeq, mapper)
}
