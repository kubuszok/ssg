/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1158 (R0610-P2): FormatUtil.toFixed diverges from the
 * ECMA-262 `Number.prototype.toFixed` semantics that Mermaid/dagre rely on
 * for SVG number formatting.
 *
 * The port computes `Math.round(value * pow)` where `pow = 10^precision`.
 * Two divergences from ECMA-262 `Number.prototype.toFixed`:
 *
 *   1. The `value * pow` float-multiply is lossy. For example, the binary64
 *      product `0.015 * 100.0` rounds up to exactly `1.5`, so
 *      `Math.round(1.5) == 2` and the port yields "0.02". ECMA toFixed
 *      operates on the exact binary64 value of `0.015` (which is just below
 *      the tie, ~0.01499999...), so `(0.015).toFixed(2) === "0.01"`.
 *
 *   2. `Math.round` is round-half-up toward +infinity, not ECMA's
 *      round-half-away-from-zero. `Math.round(-12.5) == -12`, so the port
 *      yields "-1.2", whereas `(-1.25).toFixed(1) === "-1.3"`.
 *
 * Oracle: ECMA-262 `Number.prototype.toFixed`. The expected literals below
 * were produced by Node's `Number.prototype.toFixed` and cross-checked
 * against `new java.math.BigDecimal(value).setScale(precision,
 * RoundingMode.HALF_UP).toPlainString` (the faithful target for the fix).
 * Mermaid uses JS-native toFixed, so there is no original-src line to cite.
 */
package ssg
package graphs
package commons
package util

import munit.FunSuite

final class FormatUtilToFixedTieIss1158Suite extends FunSuite {

  test("toFixed(0.015, 2) follows ECMA-262 Number.prototype.toFixed (lossy multiply)") {
    assertEquals(
      FormatUtil.toFixed(0.015, 2),
      "0.01",
      "Number.prototype.toFixed (ECMA-262): (0.015).toFixed(2) === \"0.01\" — the exact binary64 value of 0.015 is below the tie; the port's lossy `0.015 * 100.0` float-multiply rounds up to 1.5 and wrongly yields \"0.02\""
    )
  }

  test("toFixed(-1.25, 1) follows ECMA-262 Number.prototype.toFixed (round-half-away-from-zero)") {
    assertEquals(
      FormatUtil.toFixed(-1.25, 1),
      "-1.3",
      "Number.prototype.toFixed (ECMA-262): (-1.25).toFixed(1) === \"-1.3\" — ECMA rounds half away from zero; the port's Math.round is round-half-up toward +infinity and wrongly yields \"-1.2\""
    )
  }

  test("toFixed(0.125, 2) follows ECMA-262 Number.prototype.toFixed (positive away-from-zero tie)") {
    assertEquals(
      FormatUtil.toFixed(0.125, 2),
      "0.13",
      "Number.prototype.toFixed (ECMA-262): (0.125).toFixed(2) === \"0.13\""
    )
  }

  test("toFixed(-0.125, 2) follows ECMA-262 Number.prototype.toFixed (negative away-from-zero tie)") {
    assertEquals(
      FormatUtil.toFixed(-0.125, 2),
      "-0.13",
      "Number.prototype.toFixed (ECMA-262): (-0.125).toFixed(2) === \"-0.13\""
    )
  }

  test("toFixed(9.999, 2) follows ECMA-262 Number.prototype.toFixed (carry propagation)") {
    assertEquals(
      FormatUtil.toFixed(9.999, 2),
      "10.00",
      "Number.prototype.toFixed (ECMA-262): (9.999).toFixed(2) === \"10.00\""
    )
  }

  test("toFixed(1.255, 2) follows ECMA-262 Number.prototype.toFixed (binary64 below tie)") {
    assertEquals(
      FormatUtil.toFixed(1.255, 2),
      "1.25",
      "Number.prototype.toFixed (ECMA-262): (1.255).toFixed(2) === \"1.25\" — the exact binary64 value of 1.255 is ~1.25499..., below the tie"
    )
  }

  test("toFixed(2.5, 0) follows ECMA-262 Number.prototype.toFixed (away-from-zero, no fraction)") {
    assertEquals(
      FormatUtil.toFixed(2.5, 0),
      "3",
      "Number.prototype.toFixed (ECMA-262): (2.5).toFixed(0) === \"3\""
    )
  }

  test("toFixed(-2.5, 0) follows ECMA-262 Number.prototype.toFixed (negative away-from-zero, no fraction)") {
    assertEquals(
      FormatUtil.toFixed(-2.5, 0),
      "-3",
      "Number.prototype.toFixed (ECMA-262): (-2.5).toFixed(0) === \"-3\" — round half away from zero, not toward +infinity"
    )
  }
}
