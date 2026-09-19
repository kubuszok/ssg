/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1156: locale-dependent number formatting leaks into the
 * SVG rendered by ssg-mermaid. Same bug class as resolved ISS-1153 (katex
 * Units.makeEm): the Scala `f"...%.Nf"` interpolator compiles to
 * `String.format`, which on the JVM uses `Locale.getDefault` — on a JVM
 * whose default locale uses a comma decimal separator (de_DE, pl_PL, ...)
 * it emits `33,3%` / `rgba(..., 0,5)` instead of `33.3%` / `rgba(..., 0.5)`.
 *
 * Offending port sites under test here:
 *   - ssg-mermaid/src/main/scala/ssg/mermaid/diagrams/pie/PieRenderer.scala
 *     — pie slice percentage labels. These now render integer percentages via
 *     the locale-independent FormatUtil.toFixed(percentage, 0), matching
 *     upstream pieRenderer.ts:119 (ISS-1159); the historical `f"${percentage}%.1f%%"`
 *     form emitted "33,3%" under Locale.GERMANY. The comma-decimal invariant
 *     is now asserted directly on FormatUtil.toFixed plus a no-comma check on
 *     the rendered pie SVG.
 *   - ssg-mermaid/src/main/scala/ssg/mermaid/color/Color.scala:103,190,198
 *     — `f"$alpha%.2f"` / `f"$v%.2f"` in RgbaColor.formatAlpha and
 *     HslaColor.formatNumber/formatAlpha, feeding rgba()/hsl() CSS color
 *     strings (0.5 → "0,50" → trailing-zero strip → "0,5").
 *   - ssg-mermaid/src/main/scala/ssg/mermaid/util/Utils.scala:143
 *     — `f"$rounded%.4f"` in the scientific-notation branch of
 *     `formatNumber` (duplicate of the graphs-commons FormatUtil pattern).
 *   Additionally, the pie SVG path data flows through the shared
 *   graphs-commons PathData.fmt (`f"$rounded%.2f"`, PathData.scala:206),
 *   covered end-to-end by the locale-independence render comparison below.
 *
 * NOT red-testable for comma decimals (listed in ISS-1156 for fix scope,
 * documented here so it is not silently dropped): the `%.0f` sites —
 * ssg-mermaid/.../xychart/XyChartRenderer.scala:170 (`f"$value%.0f"`) —
 * request zero fraction digits, so no decimal separator (and no grouping,
 * absent the `,` flag) is ever emitted; their output is locale-independent
 * even though the call is locale-sensitive in form.
 *
 * Expected values (cited per C11):
 *   - Upstream Mermaid is JavaScript/TypeScript: ECMA-262
 *     `Number.prototype.toFixed` and `Number::toString` ALWAYS use "." as
 *     the decimal separator (no locale concept outside toLocaleString), so
 *     the original library can never emit comma decimals. Pie labels
 *     upstream: original-src/mermaid/packages/mermaid/src/diagrams/pie/pieRenderer.ts:119
 *     — `((datum.data.value / sum) * 100).toFixed(0) + '%'`. The port renders
 *     the same integer percentages via FormatUtil.toFixed(percentage, 0)
 *     (ISS-1159); the locale-independence invariant — dot decimal, never
 *     comma — carries over unchanged.
 *   - Color strings: upstream mermaid theming uses khroma (JS), whose
 *     channel/alpha values are stringified via ECMA-262 number-to-string;
 *     CSS Color / CSS Values <number> grammar admits only "." as the
 *     decimal separator, so e.g. an alpha of one half is always "0.5".
 *   - util.Utils.roundNumber/formatNumber port `roundNumber` from
 *     original-src/mermaid/packages/mermaid/src/utils.ts:329-332, whose
 *     results are stringified in JS via ECMA-262 `Number::toString`.
 *
 * JVM-only suite: `Locale.setDefault` is a JVM facility; the f-interpolator
 * only consults a default locale on the JVM.
 */
package ssg
package mermaid

import java.util.Locale

import munit.FunSuite

import ssg.graphs.commons.util.FormatUtil
import ssg.mermaid.color.{ HslaColor, RgbaColor }
import ssg.mermaid.util.Utils

class LocaleSvgIss1156JvmSuite extends FunSuite {

  // A pie chart whose slice percentages are fractional (1/3, 2/3) so the
  // integer-rounded labels are 33% and 67% (PieRenderer, integer toFixed(0),
  // ISS-1159) and the arc path data exercises fractional dot-decimal
  // formatting under a comma-decimal default locale.
  private val pieInput = "pie\n    \"a\" : 1\n    \"b\" : 2"

  // Matches the contents of every style="..." attribute in the markup.
  private val styleAttrRe = """style="([^"]*)"""".r

  // A comma used as a decimal separator between two digits — never valid in
  // CSS numeric values (upstream JS emits dot-decimals by construction).
  private val commaDecimalRe = """[0-9],[0-9]""".r

  // A comma decimal immediately before '%' — e.g. the broken pie label
  // "33,3%". Legitimate SVG path-data commas never precede '%'.
  private val commaDecimalPercentRe = """[0-9],[0-9]+%""".r

  // A percentage label — e.g. the integer pie label "33%" (upstream
  // pieRenderer.ts:119 `((value / sum) * 100).toFixed(0) + '%'`).
  private val percentRe = """[0-9]+%""".r

  // A dot decimal anywhere — e.g. the arc path data "12.34". Confirms the
  // render exercises fractional (dot-decimal) formatting even though the pie
  // percentage labels are now integers.
  private val dotDecimalRe = """[0-9]\.[0-9]""".r

  private var savedLocale: Locale = Locale.getDefault

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    savedLocale = Locale.getDefault
    // GERMANY uses a comma decimal separator: f"${33.333}%.1f" → "33,3".
    Locale.setDefault(Locale.GERMANY)
  }

  override def afterEach(context: AfterEach): Unit = {
    // Restore unconditionally so a failure never leaks the locale into
    // neighbouring suites.
    Locale.setDefault(savedLocale)
    super.afterEach(context)
  }

  test("ISS-1156: pie labels never use a comma separator; FormatUtil.toFixed is locale-independent under Locale.GERMANY") {
    // The pie now renders INTEGER percentages, matching upstream
    // pieRenderer.ts:119 `((value / sum) * 100).toFixed(0) + '%'` (ISS-1159),
    // so the former dot-decimal `33.3%` vehicle is gone. The core ISS-1156
    // protection is preserved two ways:
    //   (a) the rendered pie SVG must never carry a comma-decimal percentage
    //       under Locale.GERMANY (integer percentages must not grow a comma
    //       from grouping), and
    //   (b) the underlying formatter FormatUtil.toFixed is asserted directly
    //       to be locale-independent (ECMA-262 dot decimal, never comma).
    val svg = Mermaid.render(pieInput)
    assertEquals(
      commaDecimalPercentRe.findFirstIn(svg),
      None,
      s"pie percentage labels must never use a comma decimal separator (upstream " +
        s"pieRenderer.ts:119 stringifies via ECMA-262 toFixed, always dot); svg: $svg"
    )
    // Direct locale-independence check on the formatter that feeds the pie
    // labels (and path data): under Locale.GERMANY it must still emit a '.'
    // decimal, e.g. 33.333 → "33.3", NOT "33,3".
    assertEquals(FormatUtil.toFixed(33.333, 1), "33.3")
    assert(
      !FormatUtil.toFixed(33.333, 1).contains(','),
      "FormatUtil.toFixed must be locale-independent (dot decimal, never comma)"
    )
  }

  test("ISS-1156 invariant: pie style attributes contain no comma decimals under Locale.GERMANY") {
    // Passes today: the pie path's style values (opacity, stroke-width) are
    // theme-variable string literals, not f-interpolated numbers. Kept as a
    // guard so the ISS-1156 fix cannot regress style attributes the way the
    // labels and colors are broken (CSS <number> admits only '.').
    val svg       = Mermaid.render(pieInput)
    val styles    = styleAttrRe.findAllMatchIn(svg).map(_.group(1)).toList
    val offenders = styles.filter(s => commaDecimalRe.findFirstIn(s).isDefined)
    assert(
      offenders.isEmpty,
      s"style attributes must never contain comma decimal separators (CSS <number> " +
        s"grammar admits only '.'); offending style values: ${offenders.mkString("[", ", ", "]")}; " +
        s"full svg: $svg"
    )
  }

  test("ISS-1156 red: rendered SVG is identical under Locale.GERMANY and Locale.US") {
    // Upstream Mermaid output is locale-independent by construction
    // (ECMA-262 number stringification); the port must be too. This covers
    // every formatting site on the pie path end-to-end, including the
    // shared graphs-commons PathData/FormatUtil used for arc path data.
    val svgDe = Mermaid.render(pieInput)
    Locale.setDefault(Locale.US)
    val svgUs = Mermaid.render(pieInput)
    assert(
      dotDecimalRe.findFirstIn(svgUs).isDefined,
      s"sanity: the US render must exercise fractional (dot-decimal) formatting via arc path data; svg: $svgUs"
    )
    assert(
      percentRe.findFirstIn(svgUs).isDefined,
      s"sanity: the US render must carry an (integer) percentage label; svg: $svgUs"
    )
    assertEquals(svgDe, svgUs, "render output must not depend on the JVM default locale")
  }

  test("ISS-1156 red: RgbaColor alpha 0.5 renders as 0.5 under Locale.GERMANY") {
    // CSS Color <alpha-value> is a <number>: dot decimal only. Upstream
    // khroma (JS) stringifies via ECMA-262, so one half is always "0.5".
    assertEquals(RgbaColor(255, 0, 0, 0.5).toRgbaString, "rgba(255, 0, 0, 0.5)")
  }

  test("ISS-1156 red: HslaColor fractional channels render dot-decimal under Locale.GERMANY") {
    // HslaColor.formatNumber/formatAlpha (Color.scala:190,198): round to 2
    // decimals, strip trailing zeros, '.' separator per CSS <number>.
    assertEquals(
      HslaColor(120.5, 50.25, 40.75, 0.25).toCssString,
      "hsla(120.5, 50.25%, 40.75%, 0.25)"
    )
  }

  test("ISS-1156 red: Utils.formatNumber is dot-decimal under Locale.GERMANY") {
    // Scientific-notation branch (Utils.scala:143): 0.0001 → Double.toString
    // "1.0E-4" → f"%.4f"; 12345678.5 → "1.23456785E7" → f"%.4f" + trailing
    // zero strip. Upstream: utils.ts:329-332 roundNumber + ECMA-262
    // Number::toString — dot decimal always.
    assertEquals(Utils.formatNumber(0.0001), "0.0001")
    assertEquals(Utils.formatNumber(12345678.5), "12345678.5")
  }

  test("control: under Locale.US the same expectations hold (assertions are sound)") {
    // Pins that the expected values themselves are correct today on a
    // dot-decimal default locale — i.e. the red tests above fail only
    // because of the locale, not because of wrong expectations.
    Locale.setDefault(Locale.US)
    val svg = Mermaid.render(pieInput)
    assertEquals(commaDecimalPercentRe.findFirstIn(svg), None)
    assert(percentRe.findFirstIn(svg).isDefined, s"expected an integer percentage label; svg: $svg")
    assertEquals(FormatUtil.toFixed(33.333, 1), "33.3")
    val styles = styleAttrRe.findAllMatchIn(svg).map(_.group(1)).toList
    assert(styles.forall(s => commaDecimalRe.findFirstIn(s).isEmpty), s"comma decimal in style; svg: $svg")
    assertEquals(RgbaColor(255, 0, 0, 0.5).toRgbaString, "rgba(255, 0, 0, 0.5)")
    assertEquals(HslaColor(120.5, 50.25, 40.75, 0.25).toCssString, "hsla(120.5, 50.25%, 40.75%, 0.25)")
    assertEquals(Utils.formatNumber(0.0001), "0.0001")
    assertEquals(Utils.formatNumber(12345678.5), "12345678.5")
    // Determinism control for the DE/US comparison test: two renders under
    // the same locale must be byte-identical.
    assertEquals(Mermaid.render(pieInput), svg)
  }
}
