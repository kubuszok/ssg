/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/RichSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/RichSequence.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence

trait RichSequence extends IRichSequence[RichSequence] {}

object RichSequence {

  val NULL:        RichSequence        = RichSequenceImpl.create("", 0, 0)
  val EMPTY_ARRAY: Array[RichSequence] = new Array[RichSequence](0)

  def of(charSequence: CharSequence): RichSequence =
    RichSequenceImpl.create(charSequence, 0, charSequence.length())

  def of(charSequence: CharSequence, startIndex: Int): RichSequence =
    RichSequenceImpl.create(charSequence, startIndex, charSequence.length())

  def of(charSequence: CharSequence, startIndex: Int, endIndex: Int): RichSequence =
    RichSequenceImpl.create(charSequence, startIndex, endIndex)

  def ofSpaces(count: Int): RichSequence =
    of(RepeatedSequence.ofSpaces(count))

  def repeatOf(c: Char, count: Int): RichSequence =
    of(RepeatedSequence.repeatOf(String.valueOf(c), 0, count))

  def repeatOf(chars: CharSequence, count: Int): RichSequence =
    of(RepeatedSequence.repeatOf(chars, 0, chars.length() * count))

  def repeatOf(chars: CharSequence, startIndex: Int, endIndex: Int): RichSequence =
    of(RepeatedSequence.repeatOf(chars, startIndex, endIndex))
}
