/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/MappedRichSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/MappedRichSequence.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.sequence.builder.{ ISequenceBuilder, RichSequenceBuilder }
import ssg.md.util.sequence.mappers.CharMapper

/** A CharSequence that maps characters according to CharMapper
  */
class MappedRichSequence private (
  private val mapper: CharMapper,
  private val base:   RichSequence
) extends IRichSequenceBase[RichSequence](0)
    with RichSequence
    with MappedSequence[RichSequence] {

  override def charMapper: CharMapper = mapper

  override def charSequence: RichSequence = base

  override def charAt(index: Int): Char = mapper.map(base.charAt(index))

  def getBaseSequence: RichSequence = base

  override def length(): Int = base.length()

  override def emptyArray(): Array[RichSequence] = base.emptyArray()

  override def nullSequence(): RichSequence = base.nullSequence()

  override def sequenceOf(baseSeq: Nullable[CharSequence], startIndex: Int, endIndex: Int): RichSequence =
    if (baseSeq.isDefined && baseSeq.get.isInstanceOf[MappedRichSequence]) {
      val rs = baseSeq.get.asInstanceOf[RichSequence]
      if (startIndex == 0 && endIndex == baseSeq.get.length()) rs
      else rs.subSequence(startIndex, endIndex).toMapped(mapper)
    } else {
      new MappedRichSequence(mapper, base.sequenceOf(baseSeq, startIndex, endIndex))
    }

  @SuppressWarnings(Array("unchecked"))
  override def getBuilder[B <: ISequenceBuilder[B, RichSequence]]: B =
    RichSequenceBuilder.emptyBuilder().asInstanceOf[B]

  override def toMapped(mapper: CharMapper): RichSequence =
    if (mapper eq CharMapper.IDENTITY) this
    else new MappedRichSequence(this.mapper.andThen(mapper), base)

  override def subSequence(startIndex: Int, endIndex: Int): RichSequence = {
    val baseSequence = base.subSequence(startIndex, endIndex)
    if (baseSequence eq base) this
    else new MappedRichSequence(mapper, baseSequence)
  }
}

object MappedRichSequence {

  def mappedOf(mapper: CharMapper, baseSeq: RichSequence): RichSequence =
    mappedOf(mapper, baseSeq, 0, baseSeq.length())

  def mappedOf(mapper: CharMapper, baseSeq: RichSequence, startIndex: Int): RichSequence =
    mappedOf(mapper, baseSeq, startIndex, baseSeq.length())

  def mappedOf(mapper: CharMapper, baseSeq: RichSequence, startIndex: Int, endIndex: Int): RichSequence =
    baseSeq match {
      case _: MappedRichSequence =>
        if (startIndex == 0 && endIndex == baseSeq.length()) baseSeq.toMapped(mapper)
        else baseSeq.subSequence(startIndex, endIndex).toMapped(mapper)
      case _ =>
        new MappedRichSequence(mapper, baseSeq.subSequence(startIndex, endIndex))
    }
}
