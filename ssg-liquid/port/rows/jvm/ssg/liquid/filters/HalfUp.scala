/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Round.java, whose last three lines format the
 *   number with a HALF_UP java.text.DecimalFormat
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the JVM row. These are java's own three lines.
 */
package ssg
package liquid
package filters

/** Formats a number by a `0` / `0.00…` pattern, rounding half up. */
object HalfUp {

  def format(pattern: String, number: Double): String = {
    val formatter = new java.text.DecimalFormat(pattern)
    formatter.setRoundingMode(java.math.RoundingMode.HALF_UP)
    formatter.format(number)
  }
}
