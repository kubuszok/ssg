/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package graphs
package commons
package util

object FormatUtil {

  def roundNumber(num: Double, precision: Int = 2): Double = {
    val factor = math.pow(10, precision)
    math.round(num * factor).toDouble / factor
  }

  /** Formats `value` rounded half-away-from-zero to `precision` decimal places, faithfully mirroring ECMA-262 `Number.prototype.toFixed`: always a '.' decimal separator, exactly `precision` fraction
    * digits (zero-padded), no grouping, no E-notation.
    *
    * Uses `new java.math.BigDecimal(double)` (the exact binary64 constructor, NOT `BigDecimal.valueOf` which re-rounds through `Double.toString`) with `setScale(precision, HALF_UP)` — Java's HALF_UP
    * is round-half-away-from-zero for both signs, matching ECMA-262. `.toPlainString` is locale-independent and never emits E-notation, so this is safe on every platform (JVM/JS/Native) — never via
    * `String.format`/f-interpolator, which on the JVM follows `Locale.getDefault` and would emit comma decimals on comma-locale hosts (ISS-1156, same bug class as ISS-1153).
    */
  def toFixed(value: Double, precision: Int): String =
    // NaN/Infinity guard: BigDecimal(double) throws NumberFormatException for
    // these. ECMA-262 Number.prototype.toFixed returns "NaN"/"Infinity"/"-Infinity".
    if (value.isNaN) "NaN"
    else if (value.isPosInfinity) "Infinity"
    else if (value.isNegInfinity) "-Infinity"
    else {
      new java.math.BigDecimal(value).setScale(precision, java.math.RoundingMode.HALF_UP).toPlainString
    }

  /** Like [[toFixed]] but strips trailing zeros (and a bare trailing '.') so e.g. 0.0500 -> "0.05", 1.0 -> "1". Mirrors the unary-`+` round-trip applied to a JS `toFixed` result. Locale-independent
    * (ISS-1156).
    */
  def toFixedTrimmed(value: Double, precision: Int): String = {
    val s: String = toFixed(value, precision)
    if (s.indexOf('.') < 0) {
      s
    } else {
      var end: Int = s.length
      while (end > 0 && s.charAt(end - 1) == '0')
        end -= 1
      if (end > 0 && s.charAt(end - 1) == '.')
        end -= 1
      s.substring(0, end)
    }
  }

  def formatNumber(value: Double): String =
    if (value == value.toLong.toDouble) {
      value.toLong.toString
    } else {
      // Round to 4 decimals, strip trailing zeros, '.' separator. The previous
      // implementation routed values that stringified with an exponent (|value|
      // < 1e-3 or >= 1e7) through a locale-sensitive `f"$rounded%.4f"`; the
      // BigDecimal-based toFixedTrimmed below never produces E-notation and is
      // locale-independent, so no E-notation branch is needed (ISS-1156).
      toFixedTrimmed(value, 4)
    }
}
