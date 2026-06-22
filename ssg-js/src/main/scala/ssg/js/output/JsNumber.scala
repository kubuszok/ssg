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

/** Cross-platform JS-faithful numeric formatting. */
object JsNumber {

  /** Format a Double the way JavaScript's `Number.prototype.toString()` would.
    *
    * Integer-valued Doubles (e.g. 1.0, -3.0) are formatted without the fractional part ("1", "-3"). Non-integer or infinite values use the platform `Double.toString` with uppercase "E" normalised to
    * lowercase "e" (matching JS exponential notation).
    */
  def toJsString(num: Double): String =
    if (num == Math.floor(num) && !num.isInfinite && Math.abs(num) < 1e15) num.toLong.toString
    else num.toString.replace("E", "e")
}
