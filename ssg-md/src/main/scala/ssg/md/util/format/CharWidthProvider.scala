/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/CharWidthProvider.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/CharWidthProvider.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

import ssg.md.util.misc.CharPredicate

trait CharWidthProvider {

  def spaceWidth: Int

  def getCharWidth(c: Char): Int

  def getStringWidth(chars: CharSequence): Int =
    getStringWidth(chars, CharPredicate.NONE)

  def getStringWidth(chars: CharSequence, zeroWidthChars: CharPredicate): Int = {
    val iMax  = chars.length()
    var width = 0
    var i     = 0
    while (i < iMax) {
      val c = chars.charAt(i)
      if (!zeroWidthChars.test(c)) {
        width += getCharWidth(c)
      }
      i += 1
    }
    width
  }
}

object CharWidthProvider {

  val NULL: CharWidthProvider = new CharWidthProvider {

    override def spaceWidth: Int = 1

    override def getCharWidth(c: Char): Int = 1
  }
}
