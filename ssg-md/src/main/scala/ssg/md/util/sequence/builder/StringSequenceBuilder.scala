/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/StringSequenceBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/StringSequenceBuilder.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.Nullable

/** A Builder for non based strings. Just a string builder wrapped in a sequence builder interface
  */
final class StringSequenceBuilder private (private val segments: StringBuilder) extends ISequenceBuilder[StringSequenceBuilder, CharSequence] {

  private def this() = this(new StringBuilder())

  def this(initialCapacity: Int) = this(new StringBuilder(initialCapacity))

  override def getBuilder: StringSequenceBuilder = new StringSequenceBuilder()

  override def charAt(index: Int): Char = segments.charAt(index)

  override def append(chars: Nullable[CharSequence], startIndex: Int, endIndex: Int): StringSequenceBuilder = {
    if (chars.isDefined && chars.get.length() > 0 && startIndex < endIndex) {
      // Use underlying java.lang.StringBuilder directly to ensure correct CharSequence overload
      segments.underlying.append(chars.get.asInstanceOf[CharSequence], startIndex, endIndex)
    }
    this
  }

  override def append(c: Char): StringSequenceBuilder = {
    segments.append(c)
    this
  }

  override def append(c: Char, count: Int): StringSequenceBuilder = {
    var remaining = count
    while (remaining > 0) {
      segments.append(c)
      remaining -= 1
    }
    this
  }

  override def singleBasedSequence: Nullable[CharSequence] = Nullable(toSequence)

  override def toSequence: CharSequence = segments.toString

  override def length: Int = segments.length()

  override def toString: String = segments.toString
}

object StringSequenceBuilder {

  def emptyBuilder(): StringSequenceBuilder = new StringSequenceBuilder()
}
