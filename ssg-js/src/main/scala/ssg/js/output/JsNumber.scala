/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * JS-faithful Double-to-String formatting shared between the output stream
 * (OutputStream.numToString) and the compressor template-fold path
 * (Compressor.optimizeTemplateString).
 *
 * In JavaScript, Number(1).toString() === "1", but on the JVM/Native
 * (1.0).toString == "1.0". This helper replicates the JS behaviour so that
 * integer-valued Doubles format without a trailing ".0".
 *
 * Introduced by ISS-1175 (extending the ISS-1140 fix to the compress-fold
 * path). The logic is lifted from OutputStream.numToString
 * (output/OutputStream.scala:2134) so both call sites share ONE source of
 * truth.
 *
 * Original reference: terser uses JS implicit Number->String coercion
 * (lib/compress/index.js:3924 `result + ""`, lib/output.js number_to_string).
 */
package ssg
package js
package output

/** Cross-platform JS-faithful numeric formatting.
  *
  * Implements ECMA-262 section 6.1.6.1.20 (Number::toString) so that the output is identical to JavaScript's `Number.prototype.toString()` on all three platforms (JVM, JS, Native). The platform's
  * shortest round-tripping decimal (`Double.toString`) provides the significant digits; this helper reformats them according to the five ECMA cases.
  */
object JsNumber {

  /** Format a Double the way JavaScript's `Number.prototype.toString()` would, per ECMA-262 section 6.1.6.1.20.
    *
    * Decompose |num| into a digit string s (length k, no leading/trailing zeros) and an exponent n such that the value equals s * 10^(n-k). Then:
    *   - Case A (k <= n <= 21): s + (n-k) trailing zeros
    *   - Case B (0 < n <= 21, n < k): s[0..n] + "." + s[n..]
    *   - Case C (-6 < n <= 0): "0." + (-n) zeros + s
    *   - Case D (n > 21, k == 1): s + "e+" + (n-1)
    *   - Case E (n > 21, k > 1): s(0) + "." + s(1..) + "e+" + (n-1)
    *   - Case F (n <= -6, k == 1): s + "e-" + |n-1|
    *   - Case G (n <= -6, k > 1): s(0) + "." + s(1..) + "e-" + |n-1|
    * Negative numbers: "-" + the above on |num|.
    */
  def toJsString(num: Double): String =
    if (num.isNaN) {
      // ECMA step 1: NaN
      "NaN"
    } else if (num == 0.0) {
      // ECMA step 3: +0 / -0
      "0"
    } else if (num < 0) {
      // ECMA step 4: negative -> "-" + ToString(|x|)
      "-" + toJsString(-num)
    } else if (num.isInfinite) {
      // ECMA step 5: +Infinity
      "Infinity"
    } else {
      // Parse the platform's shortest decimal representation to extract (s, n).
      // Platform differences: JVM/Native emit uppercase "E" and a mandatory ".0"
      // (e.g. "1.0E15", "1.0E-7"); Scala.js emits lowercase "e" with sign
      // (e.g. "1e+15", "1e-7") and drops ".0".
      val raw = num.toString
      // Normalize to lowercase and split on "e" to separate mantissa from exponent.
      val lower               = raw.replace('E', 'e')
      val eIdx                = lower.indexOf('e')
      val (mantissa, expPart) =
        if (eIdx >= 0) (lower.substring(0, eIdx), lower.substring(eIdx + 1))
        else (lower, "")
      // Parse mantissa digits: strip the decimal point, track its position.
      val dotIdx                = mantissa.indexOf('.')
      val (rawDigits, pointPos) =
        if (dotIdx >= 0)
          (mantissa.substring(0, dotIdx) + mantissa.substring(dotIdx + 1), dotIdx)
        else
          (mantissa, mantissa.length)
      // Parse the exponent part (handles "+NN", "-NN", "NN", or empty).
      val parsedExp =
        if (expPart.isEmpty) 0
        else if (expPart.charAt(0) == '+') expPart.substring(1).toInt
        else expPart.toInt
      // Strip leading zeros from raw digits.
      var start = 0
      while (start < rawDigits.length && rawDigits.charAt(start) == '0')
        start += 1
      // Strip trailing zeros from raw digits.
      var end = rawDigits.length
      while (end > start && rawDigits.charAt(end - 1) == '0')
        end -= 1
      val s = rawDigits.substring(start, end) // significant digits, no leading/trailing zeros
      val k = s.length // digit count
      // n = position of the leading significant digit + parsed exponent.
      // pointPos is where the decimal point sits in rawDigits; start is the index
      // of the first non-zero digit. n = (pointPos - start) + parsedExp.
      val n = (pointPos - start) + parsedExp
      ecmaFormat(s, k, n)
    }

  /** Apply ECMA-262 NumberToString formatting given significant digits s (length k) and exponent n (value = s * 10^(n-k)).
    */
  private def ecmaFormat(s: String, k: Int, n: Int): String =
    if (k <= n && n <= 21) {
      // Case A: integer, no exponent. s followed by (n-k) zeros.
      s + "0" * (n - k)
    } else if (0 < n && n <= 21) {
      // Case B: decimal point within the digits (n < k implied by failing Case A).
      s.substring(0, n) + "." + s.substring(n)
    } else if (-6 < n && n <= 0) {
      // Case C: "0." + (-n) zeros + s
      "0." + "0" * (-n) + s
    } else if (k == 1) {
      // Case D/F: single-digit mantissa, exponent form
      s + "e" + (if (n - 1 >= 0) "+" else "") + (n - 1)
    } else {
      // Case E/G: multi-digit mantissa, exponent form
      s.charAt(0).toString + "." + s.substring(1) + "e" + (if (n - 1 >= 0) "+" else "") + (n - 1)
    }
}
