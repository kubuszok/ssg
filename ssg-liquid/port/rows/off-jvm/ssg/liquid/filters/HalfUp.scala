/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Round.java, whose last three lines format the
 *   number with a HALF_UP java.text.DecimalFormat
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the Scala.js and Scala Native rows, whose DecimalFormat (scala-java-locales) answers
 *     `4.6` for pattern `0`.
 *   Convention: java's DecimalFormat rounds the double's EXACT binary value, so the value is taken
 *     exactly (`new BigDecimal(double)`) and set to the pattern's fraction digits.
 *   Difference: the decimal separator is always `.`; java writes the default locale's, which
 *     Round's own `new PlainBigDecimal(…)` then refuses where that is a comma.
 */
package ssg
package liquid
package filters

/** Formats a number by a `0` / `0.00…` pattern, rounding half up. */
object HalfUp {

  def format(pattern: String, number: Double): String = {
    val dot   = pattern.indexOf('.')
    val scale = if (dot < 0) 0 else pattern.length - dot - 1
    new java.math.BigDecimal(number).setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString()
  }
}
