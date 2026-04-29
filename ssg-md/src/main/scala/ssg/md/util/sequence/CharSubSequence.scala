/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/CharSubSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/CharSubSequence.java
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

/** A CharSequence that references original char[] a subSequence() returns a sub-sequence from the original base sequence
  *
  * NOTE: '\0' changed to '\uFFFD' use [[ssg.md.util.sequence.mappers.NullEncoder.decodeNull]] mapper to get original null chars.
  */
final class CharSubSequence private (
  private val baseChars: Array[Char],
  private val base:      CharSubSequence,
  val startOffset:       Int,
  val endOffset:         Int,
  hash:                  Int
) extends BasedSequenceImpl(hash) {

  // Primary constructor for a root sequence from char array
  private def this(chars: Array[Char], hash: Int) =
    this(chars, null, 0, chars.length, hash) // @nowarn — null only during construction, replaced immediately

  private val resolvedBase: CharSubSequence = if (base == null) this else base // @nowarn — null check needed for self-referential base

  // Constructor for sub-sequences
  private def this(baseSeq: CharSubSequence, startIndex: Int, endIndex: Int) = {
    this(
      baseSeq.baseChars,
      baseSeq,
      baseSeq.startOffset + startIndex,
      baseSeq.startOffset + endIndex,
      0
    )
    assert(
      startIndex >= 0 && endIndex >= startIndex && endIndex <= baseSeq.baseChars.length,
      s"CharSubSequence must have (startIndex > 0 || endIndex < ${baseSeq.baseChars.length}) && endIndex >= startIndex, got startIndex:$startIndex, endIndex: $endIndex"
    )
  }

  override def optionFlags: Int = 0

  override def allOptions(options: Int): Boolean = false

  override def anyOptions(options: Int): Boolean = false

  override def getOption[T](dataKey: DataKeyBase[T]): Nullable[T] =
    Nullable(dataKey.get(Nullable.empty[DataHolder]))

  override def options: Nullable[DataHolder] = Nullable.empty[DataHolder]

  override def getBaseSequence: CharSubSequence = resolvedBase

  override def getBase: AnyRef = baseChars.asInstanceOf[AnyRef]

  override def length(): Int = endOffset - startOffset

  override def getSourceRange: Range = Range.of(startOffset, endOffset)

  override def getIndexOffset(index: Int): Int = {
    SequenceUtils.validateIndexInclusiveEnd(index, length())
    startOffset + index
  }

  override def charAt(index: Int): Char = {
    SequenceUtils.validateIndex(index, length())
    val c = baseChars(index + startOffset)
    if (c == SequenceUtils.NUL) SequenceUtils.ENC_NUL else c
  }

  override def subSequence(startIndex: Int, endIndex: Int): CharSubSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, length())
    resolvedBase.baseSubSequence(startOffset + startIndex, startOffset + endIndex)
  }

  override def baseSubSequence(startIndex: Int, endIndex: Int): CharSubSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, baseChars.length)
    if (startIndex == startOffset && endIndex == endOffset) this
    else if (resolvedBase ne this) resolvedBase.baseSubSequence(startIndex, endIndex)
    else new CharSubSequence(resolvedBase, startIndex, endIndex)
  }
}

object CharSubSequence {

  def of(charSequence: CharSequence): CharSubSequence =
    of(charSequence, 0, charSequence.length())

  def of(charSequence: CharSequence, startIndex: Int): CharSubSequence = {
    assert(startIndex <= charSequence.length())
    of(charSequence, startIndex, charSequence.length())
  }

  @deprecated("Use BasedSequence.of() for creating based sequences", "")
  def of(chars: Array[Char], startIndex: Int, endIndex: Int): CharSubSequence = {
    assert(startIndex >= 0 && startIndex <= endIndex && endIndex <= chars.length)
    val useChars = new Array[Char](chars.length)
    System.arraycopy(chars, 0, useChars, 0, chars.length)
    if (startIndex == 0 && endIndex == chars.length) new CharSubSequence(useChars, 0)
    else new CharSubSequence(useChars, 0).subSequence(startIndex, endIndex)
  }

  private def of(charSequence: CharSequence, startIndex: Int, endIndex: Int): CharSubSequence = {
    assert(startIndex >= 0 && startIndex <= endIndex && endIndex <= charSequence.length())

    val charSubSequence: CharSubSequence = charSequence match {
      case css: CharSubSequence => css
      case s:   String          =>
        new CharSubSequence(s.toCharArray, s.hashCode())
      case sb: StringBuilder =>
        val chars = sb.toString.toCharArray
        new CharSubSequence(chars, 0)
      case _ =>
        new CharSubSequence(charSequence.toString.toCharArray, 0)
    }

    if (startIndex == 0 && endIndex == charSequence.length()) charSubSequence
    else charSubSequence.subSequence(startIndex, endIndex)
  }
}
