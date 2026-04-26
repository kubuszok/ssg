/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/PrefixedSubSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.data.{ DataHolder, DataKeyBase }
import ssg.md.util.sequence.builder.IBasedSegmentBuilder

/** A BasedSequence with an out of scope of original char sequence prefix
  *
  * a subSequence() returns a sub-sequence from the original base sequence, possibly with a prefix if it falls in range
  */
final class PrefixedSubSequence private (
  private val prefix: CharSequence,
  private val base:   BasedSequence
) extends BasedSequenceImpl(0),
      ReplacedBasedSequence {

  private def this(prefix: CharSequence, baseSeq: BasedSequence, startIndex: Int, endIndex: Int) =
    this(prefix, baseSeq.subSequence(startIndex, endIndex))

  override def getBase: AnyRef = base.getBase

  override def getBaseSequence: BasedSequence = base.getBaseSequence

  override def startOffset: Int = base.startOffset

  override def endOffset: Int = base.endOffset

  override def getSourceRange: Range = base.getSourceRange

  override def baseSubSequence(startIndex: Int, endIndex: Int): BasedSequence =
    base.baseSubSequence(startIndex, endIndex)

  override def optionFlags: Int = getBaseSequence.optionFlags

  override def allOptions(options: Int): Boolean = getBaseSequence.allOptions(options)

  override def anyOptions(options: Int): Boolean = getBaseSequence.anyOptions(options)

  override def getOption[T](dataKey: DataKeyBase[T]): Nullable[T] = getBaseSequence.getOption(dataKey)

  override def options: Nullable[DataHolder] = getBaseSequence.options

  override def length(): Int = prefix.length() + base.length()

  override def getIndexOffset(index: Int): Int = {
    SequenceUtils.validateIndexInclusiveEnd(index, length())
    if (index < prefix.length()) {
      // NOTE: to allow creation of segmented sequences from modified original base return -1 for all such modified content positions
      -1
    } else {
      base.getIndexOffset(index - prefix.length())
    }
  }

  override def addSegments(builder: IBasedSegmentBuilder[?]): Unit = {
    if (prefix.length() != 0) {
      builder.append(base.startOffset, base.startOffset)
      builder.append(prefix.toString)
    }
    base.addSegments(builder)
  }

  override def charAt(index: Int): Char = {
    SequenceUtils.validateIndex(index, length())
    val prefixLength = prefix.length()
    if (index < prefixLength) prefix.charAt(index)
    else base.charAt(index - prefixLength)
  }

  override def subSequence(startIndex: Int, endIndex: Int): BasedSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, length())
    val prefixLength = prefix.length()
    if (startIndex < prefixLength) {
      if (endIndex <= prefixLength) {
        // all from prefix
        new PrefixedSubSequence(prefix.subSequence(startIndex, endIndex), base.subSequence(0, 0), 0, 0)
      } else {
        // some from prefix some from base
        new PrefixedSubSequence(prefix.subSequence(startIndex, prefixLength), base, 0, endIndex - prefixLength)
      }
    } else {
      // all from base
      base.subSequence(startIndex - prefixLength, endIndex - prefixLength)
    }
  }

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append(prefix)
    base.appendTo(sb)
    sb.toString
  }
}

object PrefixedSubSequence {

  def repeatOf(prefix: CharSequence, count: Int, baseSeq: BasedSequence): PrefixedSubSequence =
    prefixOf(RepeatedSequence.repeatOf(prefix, count).toString, baseSeq, 0, baseSeq.length())

  def repeatOf(prefix: Char, count: Int, baseSeq: BasedSequence): PrefixedSubSequence =
    prefixOf(RepeatedSequence.repeatOf(prefix, count), baseSeq, 0, baseSeq.length())

  def prefixOf(prefix: CharSequence, baseSeq: BasedSequence): PrefixedSubSequence =
    prefixOf(prefix, baseSeq, 0, baseSeq.length())

  def prefixOf(prefix: CharSequence, baseSeq: BasedSequence, startIndex: Int): PrefixedSubSequence =
    prefixOf(prefix, baseSeq, startIndex, baseSeq.length())

  def prefixOf(prefix: CharSequence, baseSeq: BasedSequence, startIndex: Int, endIndex: Int): PrefixedSubSequence =
    new PrefixedSubSequence(prefix, baseSeq, startIndex, endIndex)

  @deprecated("Use prefixOf", "")
  def of(prefix: CharSequence, baseSeq: BasedSequence): PrefixedSubSequence =
    prefixOf(prefix, baseSeq)

  @deprecated("Use prefixOf", "")
  def of(prefix: CharSequence, baseSeq: BasedSequence, startIndex: Int): PrefixedSubSequence =
    prefixOf(prefix, baseSeq, startIndex)

  @deprecated("Use prefixOf", "")
  def of(prefix: CharSequence, baseSeq: BasedSequence, startIndex: Int, endIndex: Int): PrefixedSubSequence =
    prefixOf(prefix, baseSeq, startIndex, endIndex)
}
