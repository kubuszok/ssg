/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1156: locale-dependent number formatting in the shared
 * graphs-commons SVG infrastructure. Same bug class as resolved ISS-1153
 * (katex Units.makeEm): the Scala `f"...%.Nf"` interpolator compiles to
 * `String.format`, which on the JVM uses `Locale.getDefault` — on a JVM
 * whose default locale uses a comma decimal separator (de_DE, pl_PL, ...)
 * it emits `1,5` instead of `1.5`, which is invalid as an SVG/CSS number
 * and silently corrupts SVG path data (where `,` is a coordinate separator).
 *
 * Offending port sites under test here:
 *   - ssg-graphs-commons/src/main/scala/ssg/graphs/commons/util/FormatUtil.scala:24
 *     — `f"$rounded%.4f"` in the scientific-notation branch of
 *     `formatNumber` (taken when `rounded.toString` contains an exponent,
 *     i.e. |value| < 1e-3 or >= 1e7). Under Locale.GERMANY 0.0001 becomes
 *     "0,0001" (the trailing-zero strip `replaceAll("0+$", "")` leaves the
 *     comma in place) and 12345678.5 becomes "12345678,5".
 *   - ssg-graphs-commons/src/main/scala/ssg/graphs/commons/svg/PathData.scala:206
 *     — `private def fmt` uses `f"$rounded%.2f"`; under Locale.GERMANY
 *     1.5 formats as "1,50" and the trailing-zero trim is guarded by
 *     `s.contains('.')`, which is false for "1,50", so the comma'd,
 *     un-trimmed string goes verbatim into the path `d` attribute.
 *
 * Expected values (cited per C11):
 *   - The functions' own documented intent: FormatUtil.formatNumber rounds
 *     to 4 decimals and strips trailing zeros; PathData.fmt (PathData.scala
 *     doc comment, line 194) "Formats a coordinate value with up to 2
 *     decimal places, stripping trailing zeros" — e.g. the class doc example
 *     (PathData.scala:29-36) shows `M0,0 L100,0 ...` with `.`-only numbers.
 *   - Upstream reference: this code ports/replaces Mermaid JS infrastructure.
 *     FormatUtil.roundNumber ports `roundNumber` from
 *     original-src/mermaid/packages/mermaid/src/utils.ts:329-332, and
 *     PathData replaces d3-path (see PathData.scala header Migration notes),
 *     whose serialization concatenates numbers via ECMA-262
 *     `Number::toString` — JS number-to-string conversion ALWAYS uses "."
 *     as the decimal separator (no locale concept outside toLocaleString),
 *     so the original library can never emit commas.
 *   - SVG 1.1 §4.2 (<number>) and the path-data grammar (SVG 1.1 §8.3.9),
 *     like CSS <number>, admit only "." as the decimal separator; "," is a
 *     coordinate separator in path data, so a comma decimal silently shifts
 *     every subsequent coordinate.
 *
 * JVM-only suite: `Locale.setDefault` is a JVM facility; the f-interpolator
 * only consults a default locale on the JVM.
 */
package ssg
package graphs
package commons
package util

import java.util.Locale

import munit.FunSuite

import ssg.graphs.commons.svg.PathData

class LocaleSvgIss1156JvmSuite extends FunSuite {

  private var savedLocale: Locale = Locale.getDefault

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    savedLocale = Locale.getDefault
    // GERMANY uses a comma decimal separator: f"${1.5}%.2f" → "1,50".
    Locale.setDefault(Locale.GERMANY)
  }

  override def afterEach(context: AfterEach): Unit = {
    // Restore unconditionally so a failure never leaks the locale into
    // neighbouring suites.
    Locale.setDefault(savedLocale)
    super.afterEach(context)
  }

  test("ISS-1156 red: FormatUtil.formatNumber(0.0001) is dot-decimal under Locale.GERMANY") {
    // 0.0001 → Double.toString "1.0E-4" → scientific-notation branch
    // (FormatUtil.scala:24). Intent: 4-decimal round, trailing zeros
    // stripped, "." separator (SVG 1.1 §4.2 admits only ".").
    assertEquals(FormatUtil.formatNumber(0.0001), "0.0001")
  }

  test("ISS-1156 red: FormatUtil.formatNumber(12345678.5) is dot-decimal under Locale.GERMANY") {
    // 12345678.5 → Double.toString "1.23456785E7" → scientific-notation
    // branch (FormatUtil.scala:24). Expected: "12345678.5000" with trailing
    // zeros stripped → "12345678.5".
    assertEquals(FormatUtil.formatNumber(12345678.5), "12345678.5")
  }

  test("ISS-1156 red: PathData fractional coordinates are dot-decimal under Locale.GERMANY") {
    // Per the PathData doc comment (line 194: round to 2 decimals, strip
    // trailing zeros) and the d3-path/ECMA-262 reference behaviour it
    // replaces: 1.5 → "1.5", 2.25 → "2.25", 3.75 → "3.75", 4.5 → "4.5".
    val d = PathData().moveTo(1.5, 2.25).lineTo(3.75, 4.5).toString
    assertEquals(d, "M1.5,2.25 L3.75,4.5")
  }

  test("ISS-1156 red: PathData curve coordinates contain no comma decimals under Locale.GERMANY") {
    val d = PathData().moveTo(0.5, 0.0).curveTo(0.5, 1.25, 2.75, 1.25, 2.75, 0.0).toString
    assertEquals(d, "M0.5,0 C0.5,1.25,2.75,1.25,2.75,0")
  }

  test("control: under Locale.US the same expectations hold (assertions are sound)") {
    // Pins that the expected strings themselves are correct today on a
    // dot-decimal default locale — i.e. the red tests above fail only
    // because of the locale, not because of wrong expectations.
    Locale.setDefault(Locale.US)
    assertEquals(FormatUtil.formatNumber(0.0001), "0.0001")
    assertEquals(FormatUtil.formatNumber(12345678.5), "12345678.5")
    assertEquals(PathData().moveTo(1.5, 2.25).lineTo(3.75, 4.5).toString, "M1.5,2.25 L3.75,4.5")
    assertEquals(
      PathData().moveTo(0.5, 0.0).curveTo(0.5, 1.25, 2.75, 1.25, 2.75, 0.0).toString,
      "M0.5,0 C0.5,1.25,2.75,1.25,2.75,0"
    )
  }
}
