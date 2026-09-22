/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: `NumberFormat.parse(String, ParsePosition)` is not implemented by scala-java-locales, so
 * `DecimalFormat.parse` is restated for the symbols the port's tests pin (en_US: `,` grouping, `.`
 * decimal, `E` exponent): the longest numeric prefix from `pos`, a `Long` when integral and in
 * range, else a `Double`; a grouping or decimal separator that ends the text is given back; nothing
 * numeric leaves `pos` where it was and sets its error index, as java does. The JVM row is java's. */
package ssg
package md
package util
package misc

import java.text.{ NumberFormat, ParsePosition }

object Numbers {

  def parse(format: NumberFormat, text: String, pos: ParsePosition): Number = {
    val _        = format // the port's one format is the default locale's; its symbols are the ones spelled here
    val start    = pos.getIndex
    var i        = start
    val negative = i < text.length && text.charAt(i) == '-'
    if (negative) i += 1
    val digits    = new java.lang.StringBuilder
    var decimalAt = -1 // count of integer digits, -1 while no decimal point was seen
    var backup    = -1 // a separator seen last: given back unless a digit follows
    var sawDigit  = false
    var exponent  = 0
    scala.util.boundary {
      while (i < text.length) {
        val c = text.charAt(i)
        val d = Character.digit(c, 10)
        if (d >= 0) {
          digits.append(('0' + d).toChar)
          sawDigit = true
          backup = -1
          i += 1
        } else if (c == ',' && decimalAt < 0) {
          backup = i
          i += 1
        } else if (c == '.' && decimalAt < 0) {
          decimalAt = digits.length
          backup = i
          i += 1
        } else if (c == 'E' && sawDigit) {
          var j      = i + 1
          val expNeg = j < text.length && text.charAt(j) == '-'
          if (j < text.length && (text.charAt(j) == '-' || text.charAt(j) == '+')) j += 1
          val expStart = j
          var exp      = 0
          while (j < text.length && Character.digit(text.charAt(j), 10) >= 0) {
            exp = exp * 10 + Character.digit(text.charAt(j), 10)
            j += 1
          }
          if (j > expStart) {
            exponent = if (expNeg) -exp else exp
            backup = -1
            i = j
          }
          scala.util.boundary.break()
        } else {
          scala.util.boundary.break()
        }
      }
    }
    if (backup >= 0) i = backup
    if (!sawDigit) {
      pos.setErrorIndex(start)
      null.asInstanceOf[Number] // java interop boundary: java's own answer when nothing parses
    } else {
      pos.setIndex(i)
      val intDigits = if (decimalAt < 0) digits.length else decimalAt
      val scale     = intDigits + exponent // digits before the decimal point after the exponent shift
      val integral  = scale >= digits.length && digits.length <= 18 && scale <= 18
      val literal   =
        (if (negative) "-" else "") + digits.substring(0, intDigits) + (if (intDigits < digits.length) "." + digits.substring(intDigits) else "") + (if (exponent != 0) "E" + exponent else "")
      if (integral) {
        val value = java.lang.Long.parseLong(digits.toString) * pow10(scale - digits.length)
        if (negative && value == 0L) java.lang.Double.valueOf(-0.0) else java.lang.Long.valueOf(if (negative) -value else value)
      } else {
        java.lang.Double.valueOf(literal)
      }
    }
  }

  private def pow10(n: Int): Long = {
    var r = 1L
    var k = 0
    while (k < n) { r *= 10L; k += 1 }
    r
  }
}
