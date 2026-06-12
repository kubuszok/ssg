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

  /** Formats `value` rounded half-up to `precision` decimal places using pure integer math, mirroring ECMA-262 `Number.prototype.toFixed`: always a '.' decimal separator, exactly `precision` fraction
    * digits (zero-padded), no grouping. Locale-independent on every platform (JVM/JS/Native) — never via `String.format`/f-interpolator, which on the JVM follows `Locale.getDefault` and would emit
    * comma decimals on comma-locale hosts (ISS-1156, same bug class as ISS-1153).
    */
  def toFixed(value: Double, precision: Int): String = {
    var pow: Long = 1L
    var i:   Int  = 0
    while (i < precision) {
      pow *= 10L
      i += 1
    }
    val scaled:    Long          = Math.round(value * pow.toDouble)
    val negative:  Boolean       = scaled < 0L
    val magnitude: Long          = Math.abs(scaled)
    val integral:  Long          = magnitude / pow
    val fraction:  Long          = magnitude % pow
    val sb:        StringBuilder = new StringBuilder()
    if (negative && (integral != 0L || fraction != 0L)) {
      sb.append('-')
    }
    sb.append(integral.toString)
    if (precision > 0) {
      sb.append('.')
      // Zero-pad the fractional part to `precision` digits.
      var frac: String = fraction.toString
      while (frac.length < precision)
        frac = "0" + frac
      sb.append(frac)
    }
    sb.toString
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
      // integer-math toFixedTrimmed below never produces E-notation and is
      // locale-independent, so no E-notation branch is needed (ISS-1156).
      toFixedTrimmed(value, 4)
    }
}
