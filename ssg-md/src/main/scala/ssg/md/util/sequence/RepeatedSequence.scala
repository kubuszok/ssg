/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/RepeatedSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

/** CharSequence that repeats in a wraparound the given sequence.
  *
  * Partial repeat occurs when start % length() > 0 and/or end % length() > 0
  *
  * The hashCode is purposefully matched to the string equivalent or this.toString().hashCode()
  */
class RepeatedSequence private (
  private val charSeq:    CharSequence,
  private val startIndex: Int,
  private val endIndex:   Int
) extends CharSequence {

  private var _hashCode: Int = 0

  override def length(): Int = endIndex - startIndex

  override def charAt(index: Int): Char = {
    if (index < 0 || index >= endIndex - startIndex) throw new IndexOutOfBoundsException()
    charSeq.charAt((startIndex + index) % charSeq.length())
  }

  override def subSequence(startIndex: Int, endIndex: Int): CharSequence =
    if (startIndex >= 0 && startIndex <= endIndex && endIndex <= this.endIndex - this.startIndex) {
      if (startIndex == endIndex) RepeatedSequence.NULL
      else if (startIndex == this.startIndex && endIndex == this.endIndex) this
      else new RepeatedSequence(charSeq, this.startIndex + startIndex, this.startIndex + endIndex)
    } else {
      throw new IllegalArgumentException(
        s"subSequence($startIndex, $endIndex) in RepeatedCharSequence('', ${this.startIndex}, ${this.endIndex})"
      )
    }

  def repeat(count: Int): RepeatedSequence = {
    val newEndIndex = startIndex + (this.endIndex - startIndex) * count
    if (startIndex >= this.endIndex) RepeatedSequence.NULL
    else if (this.endIndex == newEndIndex) this
    else new RepeatedSequence(charSeq, startIndex, newEndIndex)
  }

  override def hashCode(): Int = {
    var h = _hashCode
    if (h == 0 && length() > 0) {
      var i = 0
      while (i < length()) {
        h = 31 * h + charAt(i)
        i += 1
      }
      _hashCode = h
    }
    h
  }

  override def equals(obj: Any): Boolean =
    (obj.asInstanceOf[AnyRef] eq this) || (obj.isInstanceOf[CharSequence] && toString == obj.toString)

  override def toString: String = {
    val len = length()
    val sb = new java.lang.StringBuilder(len)
    var i = 0
    while (i < len) {
      sb.append(charAt(i))
      i += 1
    }
    sb.toString
  }
}

object RepeatedSequence {
  val NULL: RepeatedSequence = new RepeatedSequence("", 0, 0)

  def ofSpaces(count: Int): CharSequence = new RepeatedSequence(" ", 0, count)

  def repeatOf(c: Char, count: Int): CharSequence =
    new RepeatedSequence(String.valueOf(c), 0, count)

  def repeatOf(chars: CharSequence, count: Int): CharSequence =
    new RepeatedSequence(chars, 0, chars.length() * count)

  def repeatOf(chars: CharSequence, startIndex: Int, endIndex: Int): CharSequence =
    new RepeatedSequence(chars, startIndex, endIndex)

  @deprecated("Use repeatOf instead", "0.1.0")
  def of(c: Char, count: Int): CharSequence = repeatOf(c, count)

  @deprecated("Use repeatOf instead", "0.1.0")
  def of(chars: CharSequence, count: Int): CharSequence = repeatOf(chars, count)

  @deprecated("Use repeatOf instead", "0.1.0")
  def of(chars: CharSequence, startIndex: Int, endIndex: Int): CharSequence =
    repeatOf(chars, startIndex, endIndex)
}
