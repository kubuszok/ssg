/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/SpaceMapper.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/SpaceMapper.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence
package mappers

import ssg.md.util.misc.CharPredicate

object SpaceMapper {

  /** Space character. */
  private val SPC: Char = ' '

  /** Non-breaking space character. */
  private val NBSP: Char = '\u00A0'

  val toNonBreakSpace: CharMapper = new CharMapper {
    def map(c: Char): Char =
      if (c == SPC) NBSP else c
  }

  val fromNonBreakSpace: CharMapper = new CharMapper {
    def map(c: Char): Char =
      if (c == NBSP) SPC else c
  }

  def areEquivalent(c1: Char, c2: Char): Boolean =
    c1 == c2 || (c1 == ' ' && c2 == NBSP) || (c2 == ' ' && c1 == NBSP)

  def toSpaces(predicate: CharPredicate): CharMapper =
    new CharMapper {
      def map(c: Char): Char =
        if (predicate.test(c)) SPC else c
    }
}
