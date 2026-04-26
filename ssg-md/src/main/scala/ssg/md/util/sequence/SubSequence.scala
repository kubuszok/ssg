/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SubSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SubSequence.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.data.{ DataHolder, DataKeyBase }
import ssg.md.util.sequence.builder.IBasedSegmentBuilder

/** A BasedSequence implementation which wraps original CharSequence to provide a BasedSequence for all its subsequences, a subSequence() returns a SubSequence from the original base sequence.
  *
  * NOTE: '\0' changed to '\uFFFD' use [[ssg.md.util.sequence.mappers.NullEncoder.decodeNull]] mapper to get original null chars.
  */
final class SubSequence private (
  private val charSequence: CharSequence,
  private val baseSeq:      SubSequence,
  val startOffset:          Int,
  val endOffset:            Int,
  hash:                     Int
) extends BasedSequenceImpl(hash) {

  // Constructor for base sequence (wrapping the original CharSequence)
  private def this(charSequence: CharSequence) = {
    this(
      charSequence,
      null,
      0,
      charSequence.length(), // @nowarn — null only during construction, replaced immediately
      if (charSequence.isInstanceOf[String]) charSequence.hashCode() else 0
    )
    assert(!charSequence.isInstanceOf[BasedSequence])
  }

  // The baseSeq for a root SubSequence is itself; for sub-sequences it's the root
  // We handle the self-reference via an init override
  private val resolvedBaseSeq: SubSequence = if (baseSeq == null) this else baseSeq // @nowarn — null check needed for self-referential base

  // Constructor for sub-sequences
  private def this(subSequence: SubSequence, startIndex: Int, endIndex: Int) = {
    this(subSequence.charSequence, subSequence, startIndex, endIndex, 0)
    assert(
      startIndex >= 0 && endIndex >= startIndex && endIndex <= subSequence.length(),
      s"SubSequence must have startIndex >= 0 && endIndex >= startIndex && endIndex <= ${subSequence.length()}, got startIndex:$startIndex, endIndex: $endIndex"
    )
  }

  override def getBaseSequence: SubSequence = resolvedBaseSeq

  override def optionFlags: Int =
    charSequence match {
      case boh: BasedOptionsHolder => boh.optionFlags
      case _ => 0
    }

  override def allOptions(options: Int): Boolean =
    charSequence match {
      case boh: BasedOptionsHolder => boh.allOptions(options)
      case _ => false
    }

  override def anyOptions(options: Int): Boolean =
    charSequence match {
      case boh: BasedOptionsHolder => boh.anyOptions(options)
      case _ => false
    }

  override def getOption[T](dataKey: DataKeyBase[T]): Nullable[T] =
    charSequence match {
      case boh: BasedOptionsHolder => boh.getOption(dataKey)
      case _ => Nullable(dataKey.get(Nullable.empty[DataHolder]))
    }

  override def options: Nullable[DataHolder] =
    charSequence match {
      case boh: BasedOptionsHolder => boh.options
      case _ => Nullable.empty[DataHolder]
    }

  override def getBase: AnyRef = charSequence.asInstanceOf[AnyRef]

  override def addSegments(builder: IBasedSegmentBuilder[?]): Unit = {
    assert((builder.baseSequence eq resolvedBaseSeq) || builder.baseSequence.equals(resolvedBaseSeq))
    builder.append(startOffset, endOffset)
  }

  override def length(): Int = endOffset - startOffset

  override def getSourceRange: Range = Range.of(startOffset, endOffset)

  override def getIndexOffset(index: Int): Int = {
    SequenceUtils.validateIndexInclusiveEnd(index, length())
    startOffset + index
  }

  override def charAt(index: Int): Char = {
    SequenceUtils.validateIndex(index, length())
    val c = charSequence.charAt(index + startOffset)
    if (c == SequenceUtils.NUL) SequenceUtils.ENC_NUL else c
  }

  override def subSequence(startIndex: Int, endIndex: Int): SubSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, length())
    baseSubSequence(startOffset + startIndex, startOffset + endIndex)
  }

  override def baseSubSequence(startIndex: Int, endIndex: Int): SubSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, resolvedBaseSeq.length())
    if (startIndex == startOffset && endIndex == endOffset) this
    else if (resolvedBaseSeq ne this) resolvedBaseSeq.baseSubSequence(startIndex, endIndex)
    else new SubSequence(this, startIndex, endIndex)
  }
}

object SubSequence {

  private[sequence] def create(charSequence: Nullable[CharSequence]): BasedSequence =
    if (!charSequence.isDefined) BasedSequence.NULL
    else
      charSequence.get match {
        case bs: BasedSequence => bs
        case cs => new SubSequence(cs)
      }

  @deprecated("Use BasedSequence.of", "")
  def of(charSequence: Nullable[CharSequence]): BasedSequence =
    BasedSequence.of(charSequence)

  @deprecated("Use BasedSequence.of", "")
  def of(charSequence: Nullable[CharSequence], startIndex: Int): BasedSequence =
    BasedSequence.of(charSequence).subSequence(startIndex, if (!charSequence.isDefined) 0 else charSequence.get.length())

  @deprecated("Use BasedSequence.of", "")
  def of(charSequence: Nullable[CharSequence], startIndex: Int, endIndex: Int): BasedSequence =
    BasedSequence.of(charSequence).subSequence(startIndex, endIndex)
}
