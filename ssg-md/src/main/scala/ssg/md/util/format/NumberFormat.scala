/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/NumberFormat.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/NumberFormat.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

enum NumberFormat extends java.lang.Enum[NumberFormat] {
  case NONE
  case ARABIC
  case LETTERS
  case ROMAN
  case CUSTOM
}

object NumberFormat {

  def getFormat(format: NumberFormat, count: Int): String =
    format match {
      case NumberFormat.NONE    => ""
      case NumberFormat.ARABIC  => String.valueOf(count)
      case NumberFormat.LETTERS =>
        if (count < 1) throw new NumberFormatException("Letter format count must be > 0, actual " + count)
        getFormat(count - 1, "abcdefghijklmnopqrstuvwxyz")
      case NumberFormat.ROMAN  => RomanNumeral(count).toString
      case NumberFormat.CUSTOM =>
        throw new IllegalStateException("CounterFormat.CUSTOM has to use custom conversion, possibly by calling getFormat(int count, CharSequence digitSet)")
    }

  def getFormat(count: Int, digitSet: CharSequence): String = {
    val sb        = new StringBuilder(10)
    val base      = digitSet.length()
    var remaining = count
    while {
      val next = remaining / base
      val dig  = remaining - next * base
      sb.append(digitSet.charAt(dig))
      remaining = next
      remaining > 0
    } do ()

    val iMax = sb.length()
    val out  = new StringBuilder(iMax)
    var i    = iMax
    while (i > 0) {
      i -= 1
      out.append(sb.charAt(i))
    }

    out.toString
  }
}
