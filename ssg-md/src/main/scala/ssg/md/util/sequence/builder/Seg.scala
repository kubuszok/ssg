/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/Seg.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/Seg.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.Nullable
import ssg.md.util.misc.Utils.escapeJavaString
import ssg.md.util.sequence.Range

/** Representation of a segment part in a segment list for a sequence it is a Range, either in the base sequence or in the out of base characters for the builder.
  *
  * Out of base text offsets are limited to 1GB. Upper bit is used to store repeated and ascii only flags.
  */
final class Seg private (val start: Int, val end: Int) {

  def segStart: Int = if (isText) textStart else start

  def segEnd: Int = if (isText) textEnd else end

  def textStart: Int = Seg.getTextOffset(start)

  def textEnd: Int = Seg.getTextOffset(end)

  def isFirst256Start: Boolean = Seg.isFirst256Start(start)

  def isRepeatedTextEnd: Boolean = Seg.isRepeatedTextEnd(end)

  def isText: Boolean = start < 0 && end < 0 && (start & Seg.MAX_TEXT_OFFSET) > (end & Seg.MAX_TEXT_OFFSET)

  /** Test segment type being from original sequence
    *
    * @return
    *   true if it is
    */
  def isBase: Boolean = start >= 0 && end >= 0 && start <= end

  /** Test segment type being from original sequence
    *
    * @return
    *   true if it is
    */
  def isAnchor: Boolean = start >= 0 && end >= 0 && start == end

  def isNull: Boolean = !(isBase || isText)

  def getRange: Range = {
    assert(isBase)
    Range.of(start, end)
  }

  /** Return length of text or if text is null span of range
    *
    * @return
    *   length of this part in the sequence
    */
  def length: Int =
    if (isBase) end - start
    else if (isText) (start & Seg.MAX_TEXT_OFFSET) - (end & Seg.MAX_TEXT_OFFSET)
    else 0

  def toString(allText: CharSequence): String =
    if (this.isNull) {
      "NULL"
    } else if (isBase) {
      if (start == end) {
        "[" + start + ")"
      } else {
        "[" + start + ", " + end + ")"
      }
    } else {
      val charSequence = allText.subSequence(textStart, textEnd)

      if (isRepeatedTextEnd && length > 1) {
        if (isFirst256Start) {
          "a:" + (length + "x'" + escapeJavaString(Nullable(charSequence.subSequence(0, 1))) + "'")
        } else {
          "" + (length + "x'" + escapeJavaString(Nullable(charSequence.subSequence(0, 1))) + "'")
        }
      } else {
        val chars =
          if (length <= 20) charSequence.toString
          else charSequence.subSequence(0, 10).toString + "\u2026" + charSequence.subSequence(length - 10, length).toString
        if (isFirst256Start) {
          "a:'" + escapeJavaString(Nullable(chars: CharSequence)) + "'"
        } else {
          "'" + escapeJavaString(Nullable(chars: CharSequence)) + "'"
        }
      }
    }

  override def toString: String =
    if (this.isNull) {
      "NULL"
    } else if (isBase) {
      if (start == end) {
        "BASE[" + start + ")"
      } else {
        "BASE[" + start + ", " + end + ")"
      }
    } else {
      "TEXT[" + textStart + ", " + textEnd + ")"
    }
}

object Seg {

  val NULL:            Seg = new Seg(Range.NULL.start, Range.NULL.end)
  val ANCHOR_0:        Seg = new Seg(0, 0)
  val MAX_TEXT_OFFSET: Int = Int.MaxValue >> 1
  val F_TEXT_OPTION:   Int = Int.MaxValue & ~MAX_TEXT_OFFSET

  def segOf(startOffset: Int, endOffset: Int): Seg =
    if (startOffset == 0 && endOffset == 0) ANCHOR_0 else new Seg(startOffset, endOffset)

  def getTextOffset(startOffset: Int): Int =
    (-startOffset - 1) & MAX_TEXT_OFFSET

  def isFirst256Start(start: Int): Boolean =
    ((-start - 1) & F_TEXT_OPTION) != 0

  def isRepeatedTextEnd(end: Int): Boolean =
    ((-end - 1) & F_TEXT_OPTION) != 0

  def getTextStart(startOffset: Int, isFirst256: Boolean): Int = {
    assert(startOffset < MAX_TEXT_OFFSET)
    -(if (isFirst256) startOffset | F_TEXT_OPTION else startOffset) - 1
  }

  def getTextEnd(startOffset: Int, isRepeatedText: Boolean): Int = {
    assert(startOffset < MAX_TEXT_OFFSET)
    -(if (isRepeatedText) startOffset | F_TEXT_OPTION else startOffset) - 1
  }

  def textOf(startOffset: Int, endOffset: Int, isFirst256: Boolean, isRepeatedText: Boolean): Seg =
    new Seg(getTextStart(startOffset, isFirst256), getTextEnd(endOffset, isRepeatedText))
}
