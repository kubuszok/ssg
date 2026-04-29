/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/BasedOptionsSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/BasedOptionsSequence.java
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
import ssg.md.util.misc.BitFieldSet

/** A BasedSequence with offset tracking that follows editing operations and subSequence() chopping as best as it can
  *
  * a subSequence() returns a sub-sequence from the original base sequence with updated offset tracking
  */
final class BasedOptionsSequence private (
  private val charSeq: CharSequence,
  val optionFlags:     Int,
  val options:         Nullable[DataHolder]
) extends CharSequence
    with BasedOptionsHolder {

  override def allOptions(options: Int): Boolean =
    (optionFlags & options) == options

  override def anyOptions(options: Int): Boolean =
    (optionFlags & options) != 0

  override def getOption[T](dataKey: DataKeyBase[T]): Nullable[T] =
    Nullable(dataKey.get(options))

  override def length(): Int = charSeq.length()

  override def charAt(index: Int): Char = charSeq.charAt(index)

  override def subSequence(start: Int, end: Int): CharSequence = charSeq.subSequence(start, end)

  override def toString: String = charSeq.toString

  @SuppressWarnings(Array("unchecked"))
  override def equals(o: Any): Boolean =
    charSeq.equals(o)

  override def hashCode(): Int =
    charSeq.hashCode()
}

object BasedOptionsSequence {

  def of(chars: CharSequence, optionFlags: BitFieldSet[BasedOptionsHolder.Options]): BasedOptionsSequence =
    new BasedOptionsSequence(chars, optionFlags.toInt, Nullable.empty[DataHolder])

  def of(chars: CharSequence, optionFlags: Int): BasedOptionsSequence =
    new BasedOptionsSequence(chars, optionFlags, Nullable.empty[DataHolder])

  def of(chars: CharSequence, optionFlags: BitFieldSet[BasedOptionsHolder.Options], options: Nullable[DataHolder]): BasedOptionsSequence = {
    val flags = optionFlags.toInt & ~(if (!options.isDefined || !Nullable(BasedOptionsHolder.SEGMENTED_STATS.get(options)).isDefined) BasedOptionsHolder.F_COLLECT_SEGMENTED_STATS else 0)
    new BasedOptionsSequence(chars, flags, options)
  }

  def of(chars: CharSequence, optionFlags: Int, options: Nullable[DataHolder]): BasedOptionsSequence = {
    val flags = optionFlags & ~(if (!options.isDefined || !Nullable(BasedOptionsHolder.SEGMENTED_STATS.get(options)).isDefined) BasedOptionsHolder.F_COLLECT_SEGMENTED_STATS else 0)
    new BasedOptionsSequence(chars, flags, options)
  }
}
