/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Pinning test for ISS-1298: JsNumber.toJsString must implement ECMA-262
 * NumberToString (section 6.1.6.1.20) faithfully across JVM, JS, and Native.
 *
 * The original terser defers to JS runtime Number.prototype.toString() which
 * follows the ECMA spec. The previous SSG impl diverged for large/exponential
 * magnitudes (e.g. 1e15 -> "1.0e15" instead of "1000000000000000").
 *
 * Expected values below are verified against ECMA-262 section 6.1.6.1.20 and
 * confirmed by `node -e "console.log(String(...))"` for each input.
 */
package ssg
package js
package output

final class JsNumberEcmaIss1298Suite extends munit.FunSuite {

  private def check(num: Double, expected: String, clue: String): Unit =
    assertEquals(JsNumber.toJsString(num), expected, clue)

  // ECMA-262 6.1.6.1.20 step 1: NaN -> "NaN"
  test("ISS-1298 NaN -> NaN") {
    check(Double.NaN, "NaN", "NaN (ECMA step 1)")
  }

  // ECMA-262 6.1.6.1.20 step 3: +0 and -0 -> "0"
  test("ISS-1298 positive zero -> 0") {
    check(0.0, "0", "+0 (ECMA step 3)")
  }

  test("ISS-1298 negative zero -> 0") {
    check(-0.0, "0", "-0 (ECMA step 3)")
  }

  // ECMA-262 6.1.6.1.20 step 4: negative -> "-" + ToString(|x|)
  test("ISS-1298 negative integer") {
    check(-3.0, "-3", "negative integer (ECMA step 4)")
  }

  // ECMA-262 6.1.6.1.20 step 5: +/-Infinity -> "Infinity"/"-Infinity"
  test("ISS-1298 positive infinity -> Infinity") {
    check(Double.PositiveInfinity, "Infinity", "+Infinity (ECMA step 5)")
  }

  test("ISS-1298 negative infinity -> -Infinity") {
    check(Double.NegativeInfinity, "-Infinity", "-Infinity (ECMA step 5)")
  }

  // ECMA-262 6.1.6.1.20 step 8 case (k <= n <= 21): integer with trailing zeros
  test("ISS-1298 small integer 1 -> 1") {
    check(1.0, "1", "integer 1 (ECMA case k<=n<=21)")
  }

  test("ISS-1298 integer 100 -> 100") {
    check(100.0, "100", "integer 100")
  }

  test("ISS-1298 1e15 -> 1000000000000000") {
    check(1e15, "1000000000000000", "1e15 (ECMA case k<=n<=21, n=16)")
  }

  test("ISS-1298 1.2345678901234568e17 -> 123456789012345680") {
    check(1.2345678901234568e17, "123456789012345680", "1.2345678901234568e17 (ECMA case k<=n<=21)")
  }

  test("ISS-1298 1e20 -> 100000000000000000000") {
    check(1e20, "100000000000000000000", "1e20 (ECMA case k<=n<=21, n=21)")
  }

  // ECMA-262 6.1.6.1.20 step 9 (0 < n <= 21): decimal point within digits
  test("ISS-1298 decimal 0.5 -> 0.5") {
    check(0.5, "0.5", "0.5 (ECMA case 0<n<=21)")
  }

  test("ISS-1298 decimal 0.1 -> 0.1") {
    check(0.1, "0.1", "0.1 (ECMA case 0<n<=21)")
  }

  // ECMA-262 6.1.6.1.20 step 10 (-6 < n <= 0): "0." + zeros + digits
  test("ISS-1298 1e-6 -> 0.000001") {
    check(1e-6, "0.000001", "1e-6 (ECMA case -6<n<=0)")
  }

  // ECMA-262 6.1.6.1.20 step 11/12: exponent form
  test("ISS-1298 1e21 -> 1e+21") {
    check(1e21, "1e+21", "1e21 (ECMA step 11, n>21)")
  }

  test("ISS-1298 1e-7 -> 1e-7") {
    check(1e-7, "1e-7", "1e-7 (ECMA step 12, n<=-6)")
  }

  test("ISS-1298 Double.MaxValue -> 1.7976931348623157e+308") {
    check(1.7976931348623157e308, "1.7976931348623157e+308", "Double.MaxValue (ECMA step 11, large exponent)")
  }
}
