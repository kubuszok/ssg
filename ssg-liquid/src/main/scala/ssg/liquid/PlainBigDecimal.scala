/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/PlainBigDecimal.java
 * Original: Copyright (c) Christian Kohlschütter
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Idiom: Avoids toPlainString() which is broken on Scala Native.
 *          Uses manual formatting to avoid scientific notation.
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/PlainBigDecimal.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid

import java.math.BigDecimal

/** A BigDecimal with a toString() method that avoids scientific notation.
  *
  * Uses the string representation directly rather than toPlainString() for cross-platform compatibility (toPlainString is broken on Native).
  */
final class PlainBigDecimal(value: String) extends BigDecimal(value) {

  override def toString: String = {
    // Use the stored string value to avoid toPlainString() issues on Native
    val s = super.toString
    // If it contains E notation, convert manually
    if (s.contains('E') || s.contains('e')) {
      PlainBigDecimal.removeSciNotation(s)
    } else {
      s
    }
  }
}

object PlainBigDecimal {

  def apply(bd: BigDecimal): PlainBigDecimal = {
    // Convert to string without using toPlainString
    val s     = bd.toString
    val plain = if (s.contains('E') || s.contains('e')) removeSciNotation(s) else s
    new PlainBigDecimal(plain)
  }

  def apply(value: String): PlainBigDecimal =
    new PlainBigDecimal(value)

  /** Converts a scientific notation string like "1.5E+2" to "150". */
  private def removeSciNotation(s: String): String =
    // Parse and reconstruct without scientific notation
    try {
      val bd = new BigDecimal(s)
      // Use unscaledValue and scale to reconstruct
      val unscaled = bd.unscaledValue().toString
      val scale    = bd.scale()
      if (scale <= 0) {
        // Positive exponent: append zeros
        unscaled + "0" * (-scale)
      } else if (scale >= unscaled.length) {
        // All decimal: 0.00...digits
        "0." + "0" * (scale - unscaled.length) + unscaled
      } else {
        // Insert decimal point
        val intPart  = unscaled.substring(0, unscaled.length - scale)
        val fracPart = unscaled.substring(unscaled.length - scale)
        intPart + "." + fracPart
      }
    } catch {
      case _: Exception => s
    }
}
