/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/LineInfo.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.util.misc.{ BitField, BitFieldSet, Utils }

/** Line information in LineAppendable
  */
final class LineInfo private (
  val lineSeq:         CharSequence,
  val index:           Int,
  val prefixLength:    Int,
  val textLength:      Int,
  val length:          Int,
  sumPrefixLengthPrev: Int,
  sumTextLengthPrev:   Int,
  sumLengthPrev:       Int,
  initBlankPrefix:     Boolean,
  initBlankText:       Boolean,
  preformatted:        LineInfo.Preformatted
) {

  val sumPrefixLength: Int = sumPrefixLengthPrev + prefixLength
  val sumTextLength:   Int = sumTextLengthPrev + textLength
  val sumLength:       Int = sumLengthPrev + length

  val flags: Int = {
    assert((lineSeq eq BasedSequence.NULL) && index == -1 || prefixLength + textLength < length, "Line must be terminated by an EOL")
    assert(lineSeq.length() == length)

    (if (initBlankPrefix || prefixLength == 0) LineInfo.F_BLANK_PREFIX else 0) |
      (if (initBlankText || textLength == 0) LineInfo.F_BLANK_TEXT else 0) |
      preformatted.ordinal
  }

  /** See if replacing this line info with another requires updating all following line info because of aggregation change
    *
    * @param other
    *   line info
    * @return
    *   true if need to update
    */
  def needAggregateUpdate(other: LineInfo): Boolean =
    this.sumPrefixLength != other.sumPrefixLength || this.sumTextLength != other.sumTextLength || this.sumLength != other.sumLength

  def isNull: Boolean = this eq LineInfo.NULL

  def isNotNull: Boolean = !(this eq LineInfo.NULL)

  def isBlankPrefix: Boolean = BitFieldSet.any(flags, LineInfo.F_BLANK_PREFIX)

  def isBlankText: Boolean = BitFieldSet.any(flags, LineInfo.F_BLANK_TEXT)

  def isPreformatted: Boolean = BitFieldSet.any(flags, LineInfo.F_PREFORMATTED)

  def getPreformatted: LineInfo.Preformatted = LineInfo.Preformatted.get(flags)

  /** NOTE: a line which consists of any prefix and blank text is considered a blank line
    *
    * @return
    *   true if the line is a blank line
    */
  def isBlankTextAndPrefix: Boolean = BitFieldSet.all(flags, LineInfo.F_BLANK_PREFIX | LineInfo.F_BLANK_TEXT)

  def getTextStart: Int = prefixLength

  def getTextEnd: Int = prefixLength + textLength

  def getLine: BasedSequence =
    lineSeq match {
      case bs: BasedSequence => bs
      case _ => BasedSequence.of(ssg.md.Nullable(lineSeq))
    }

  def getPrefix: BasedSequence = getLine.subSequence(0, prefixLength)

  def getTextNoEOL: BasedSequence = getLine.subSequence(prefixLength, prefixLength + textLength)

  def getText: BasedSequence = getLine.subSequence(prefixLength, length)

  def getLineNoEOL: BasedSequence = getLine.subSequence(0, prefixLength + textLength)

  def getEOL: BasedSequence = getLine.subSequence(prefixLength + textLength, length)

  override def toString: String =
    "LineInfo{" +
      "i=" + index +
      ", pl=" + prefixLength +
      ", tl=" + textLength +
      ", l=" + length +
      ", sumPl=" + sumPrefixLength +
      ", sumTl=" + sumTextLength +
      ", sumL=" + sumLength +
      (if (flags != 0) "," + (if (isBlankPrefix) " bp" else "") + (if (isBlankText) " bt" else "") + (if (isPreformatted) " p" else "") else "") +
      ", '" + Utils.escapeJavaString(ssg.md.Nullable(lineSeq)) + "'" +
      "}"
}

object LineInfo {

  enum Flags(val bits: Int) extends java.lang.Enum[Flags] with BitField {
    case PREFORMATTED extends Flags(2)
    case BLANK_PREFIX extends Flags(1)
    case BLANK_TEXT extends Flags(1)
  }

  enum Preformatted extends java.lang.Enum[Preformatted] {
    case NONE, FIRST, BODY, LAST

    val mask: Int = BitFieldSet.setBitField(0, Flags.PREFORMATTED, ordinal())
  }

  object Preformatted {
    def get(flags: Int): Preformatted = {
      val preformatted = flags & F_PREFORMATTED
      if (preformatted == Preformatted.FIRST.mask) Preformatted.FIRST
      else if (preformatted == Preformatted.BODY.mask) Preformatted.BODY
      else if (preformatted == Preformatted.LAST.mask) Preformatted.LAST
      else Preformatted.NONE
    }
  }

  val BLANK_PREFIX: Flags = Flags.BLANK_PREFIX
  val BLANK_TEXT:   Flags = Flags.BLANK_TEXT
  val PREFORMATTED: Flags = Flags.PREFORMATTED

  val F_PREFORMATTED: Int = BitFieldSet.intMask(Flags.PREFORMATTED)
  val F_BLANK_PREFIX: Int = BitFieldSet.intMask(Flags.BLANK_PREFIX)
  val F_BLANK_TEXT:   Int = BitFieldSet.intMask(Flags.BLANK_TEXT)

  val NULL: LineInfo = new LineInfo(BasedSequence.NULL, -1, 0, 0, 0, 0, 0, 0, true, true, Preformatted.NONE)

  def create(
    line:          CharSequence,
    prefixLength:  Int,
    textLength:    Int,
    length:        Int,
    isBlankPrefix: Boolean,
    isBlankText:   Boolean,
    preformatted:  Preformatted
  ): LineInfo =
    new LineInfo(line, 0, prefixLength, textLength, length, 0, 0, 0, isBlankPrefix, isBlankText, preformatted)

  def create(
    line:          CharSequence,
    prevInfo:      LineInfo,
    prefixLength:  Int,
    textLength:    Int,
    length:        Int,
    isBlankPrefix: Boolean,
    isBlankText:   Boolean,
    preformatted:  Preformatted
  ): LineInfo =
    new LineInfo(
      line,
      prevInfo.index + 1,
      prefixLength,
      textLength,
      length,
      prevInfo.sumPrefixLength,
      prevInfo.sumTextLength,
      prevInfo.sumLength,
      isBlankPrefix,
      isBlankText,
      preformatted
    )

  def create(prevInfo: LineInfo, info: LineInfo): LineInfo =
    new LineInfo(
      info.lineSeq,
      prevInfo.index + 1,
      info.prefixLength,
      info.textLength,
      info.length,
      prevInfo.sumPrefixLength,
      prevInfo.sumTextLength,
      prevInfo.sumLength,
      info.isBlankPrefix,
      info.isBlankText,
      info.getPreformatted
    )
}
