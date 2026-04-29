/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/ISequenceBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/ISequenceBuilder.java
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

trait ISequenceBuilder[T <: ISequenceBuilder[T, S], S <: CharSequence] extends java.lang.Appendable {

  /** NOTE: returns non-null value if the underlying [[IBasedSegmentBuilder.getBaseSubSequenceRange]] returns non-null value
    *
    * @return
    *   sub-sequence of base representing the single segment or null if sequence not representable by a single subsequence
    */
  def singleBasedSequence: Nullable[S]

  def getBuilder: T

  def addAll(sequences: Iterable[? <: CharSequence]): T =
    append(sequences)

  def charAt(index: Int): Char

  def append(sequences: Iterable[? <: CharSequence]): T = {
    sequences.foreach { chars =>
      append(chars, 0, chars.length())
    }
    this.asInstanceOf[T]
  }

  def add(chars: Nullable[CharSequence]): T =
    append(chars)

  def append(chars: Nullable[CharSequence]): T =
    if (chars.isDefined) {
      append(chars.get, 0, chars.get.length())
    } else {
      this.asInstanceOf[T]
    }

  def append(chars: Nullable[CharSequence], startIndex: Int): T =
    if (chars.isDefined) {
      append(chars.get, startIndex, chars.get.length())
    } else {
      this.asInstanceOf[T]
    }

  def append(chars: Nullable[CharSequence], startIndex: Int, endIndex: Int): T

  def append(c: Char): T

  def append(c: Char, count: Int): T

  def toSequence: S

  def length: Int

  def isEmpty: Boolean = length <= 0

  def isNotEmpty: Boolean = length > 0

  // ---- java.lang.Appendable bridge methods ----

  override def append(csq: CharSequence): T =
    append(Nullable(csq))

  override def append(csq: CharSequence, start: Int, end: Int): T =
    append(Nullable(csq), start, end)
}
