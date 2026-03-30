/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/RichSequenceBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.Nullable
import ssg.md.util.sequence.RichSequence

/** A Builder for non based strings. Just a string builder wrapped in a sequence builder interface and wrapping result in RichSequence
  */
final class RichSequenceBuilder private (private val segments: StringBuilder) extends ISequenceBuilder[RichSequenceBuilder, RichSequence] {

  private def this() = this(new StringBuilder())

  def this(initialCapacity: Int) = this(new StringBuilder(initialCapacity))

  override def getBuilder: RichSequenceBuilder = new RichSequenceBuilder()

  override def charAt(index: Int): Char = segments.charAt(index)

  override def append(chars: Nullable[CharSequence], startIndex: Int, endIndex: Int): RichSequenceBuilder = {
    if (chars.isDefined && chars.get.length() > 0 && startIndex < endIndex) {
      segments.underlying.append(chars.get.asInstanceOf[CharSequence], startIndex, endIndex)
    }
    this
  }

  override def append(c: Char): RichSequenceBuilder = {
    segments.append(c)
    this
  }

  override def append(c: Char, count: Int): RichSequenceBuilder = {
    var remaining = count
    while (remaining > 0) {
      segments.append(c)
      remaining -= 1
    }
    this
  }

  override def singleBasedSequence: Nullable[RichSequence] = Nullable(toSequence)

  override def toSequence: RichSequence = RichSequence.of(segments)

  override def length: Int = segments.length()

  override def toString: String = segments.toString
}

object RichSequenceBuilder {

  def emptyBuilder(): RichSequenceBuilder = new RichSequenceBuilder()
}
