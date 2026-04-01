/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/RichSequenceImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.sequence.builder.{ ISequenceBuilder, RichSequenceBuilder }
import ssg.md.util.sequence.mappers.CharMapper

/** A RichSequence implementation
  *
  * NOTE: '\0' changed to '\uFFFD' use [[ssg.md.util.sequence.mappers.NullEncoder.decodeNull]] mapper to get original null chars.
  */
class RichSequenceImpl private (val charSequence: CharSequence)
    extends IRichSequenceBase[RichSequence](
      if (charSequence.isInstanceOf[String]) charSequence.hashCode() else 0
    ),
    RichSequence {

  override def emptyArray(): Array[RichSequence] = RichSequence.EMPTY_ARRAY

  override def nullSequence(): RichSequence = RichSequence.NULL

  override def sequenceOf(charSequence: Nullable[CharSequence], startIndex: Int, endIndex: Int): RichSequence =
    RichSequenceImpl.of(charSequence, startIndex, endIndex)

  @SuppressWarnings(Array("unchecked"))
  override def getBuilder[B <: ISequenceBuilder[B, RichSequence]]: B =
    RichSequenceBuilder.emptyBuilder().asInstanceOf[B]

  override def subSequence(startIndex: Int, endIndex: Int): RichSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, length())
    if (startIndex == 0 && endIndex == charSequence.length()) this
    else RichSequenceImpl.create(charSequence, startIndex, endIndex)
  }

  override def length(): Int = charSequence.length()

  override def charAt(index: Int): Char = {
    val c = charSequence.charAt(index)
    if (c == SequenceUtils.NUL) SequenceUtils.ENC_NUL else c
  }

  override def toMapped(mapper: CharMapper): RichSequence =
    MappedRichSequence.mappedOf(mapper, this)
}

object RichSequenceImpl {

  private[sequence] def create(charSequence: CharSequence, startIndex: Int, endIndex: Int): RichSequence =
    charSequence match {
      case rs: RichSequence => rs.subSequence(startIndex, endIndex)
      case cs if cs != null => // @nowarn — null check at Java interop boundary
        if (startIndex == 0 && endIndex == cs.length()) new RichSequenceImpl(cs)
        else new RichSequenceImpl(cs.subSequence(startIndex, endIndex))
      case _ => RichSequence.NULL
    }

  @deprecated("Use RichSequence.of", "")
  def of(charSequence: Nullable[CharSequence]): RichSequence =
    if (!charSequence.isDefined) RichSequence.NULL
    else RichSequence.of(charSequence.get, 0, charSequence.get.length())

  @deprecated("Use RichSequence.of", "")
  def of(charSequence: Nullable[CharSequence], startIndex: Int): RichSequence =
    if (!charSequence.isDefined) RichSequence.NULL
    else RichSequence.of(charSequence.get, startIndex, charSequence.get.length())

  @deprecated("Use RichSequence.of", "")
  def of(charSequence: Nullable[CharSequence], startIndex: Int, endIndex: Int): RichSequence =
    if (!charSequence.isDefined) RichSequence.NULL
    else RichSequence.of(charSequence.get, startIndex, endIndex)
}
