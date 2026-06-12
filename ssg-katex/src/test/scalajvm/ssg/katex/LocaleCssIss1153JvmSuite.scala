/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1153: locale-dependent number formatting leaks into the
 * rendered CSS. On a JVM whose default locale uses a comma decimal separator
 * (e.g. Locale.GERMANY, or the pl/en_PL locale this was observed on), em
 * values are rendered as `height:0,6889em` — invalid CSS that breaks layout.
 *
 * Offending port site: ssg/katex/data/Units.scala `makeEm` formats the
 * rounded value with `f"$rounded%.4f"`. The Scala `f` interpolator compiles
 * to `String.format`, which on the JVM uses `Locale.getDefault` — so the
 * decimal separator follows the JVM default locale.
 *
 * Upstream reference (original-src/katex, v0.16.45), cited per C11:
 *   - src/units.ts:99-104 — `export const makeEm = function(n: number):
 *     string { return +n.toFixed(4) + "em"; };`. ECMA-262
 *     `Number.prototype.toFixed` and `Number::toString` (invoked by the
 *     unary `+` round-trip and the string concatenation) ALWAYS emit "." as
 *     the decimal separator — JS number formatting has no locale concept
 *     outside `toLocaleString`. Upstream therefore emits dot-decimals
 *     unconditionally.
 *   - src/domTree.js `toMarkup` (ported as ssg/katex/tree/DomTree.scala)
 *     concatenates the pre-formatted style values verbatim into
 *     `style="..."`, so every fractional height/depth/kern written through
 *     `makeEm` ends up in the markup exactly as formatted.
 *
 * Expected values, from the original source: `x^2` produces fractional strut
 * heights (e.g. upstream emits `height:0.8141em` for the superscript strut),
 * always dot-decimal regardless of the host locale.
 *
 * JVM-only suite: `Locale.setDefault` is a JVM facility; Scala.js and Scala
 * Native have no settable default-locale concept affecting String.format the
 * same way. The cross-platform invariant is pinned by
 * LocaleCssIss1153SharedSuite.
 */
package ssg
package katex

import java.util.Locale

class LocaleCssIss1153JvmSuite extends KaTeXTestSuite {

  // A formula producing fractional em strut heights in the style attributes.
  private val formula = "x^2"

  // Matches the contents of every style="..." attribute in the markup.
  private val styleAttrRe = """style="([^"]*)"""".r

  // A comma used as a decimal separator between two digits — never valid in
  // CSS numeric values (upstream always emits dot-decimals, units.ts:103).
  private val commaDecimalRe = """[0-9],[0-9]""".r

  private var savedLocale: Locale = Locale.getDefault

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    savedLocale = Locale.getDefault
    // GERMANY uses a comma decimal separator: f"${0.6889}%.4f" → "0,6889".
    Locale.setDefault(Locale.GERMANY)
  }

  override def afterEach(context: AfterEach): Unit = {
    // Restore unconditionally so a failure never leaks the locale into
    // neighbouring suites.
    Locale.setDefault(savedLocale)
    super.afterEach(context)
  }

  private def styleValues(markup: String): List[String] =
    styleAttrRe.findAllMatchIn(markup).map(_.group(1)).toList

  test("ISS-1153 red: under Locale.GERMANY style attributes contain no comma decimals") {
    val markup = KaTeX.renderToString(formula)
    val styles = styleValues(markup)
    assert(styles.nonEmpty, s"expected style attributes in the markup; markup: $markup")
    val offenders = styles.filter(s => commaDecimalRe.findFirstIn(s).isDefined)
    assert(
      offenders.isEmpty,
      s"style attributes must never contain comma decimal separators (upstream units.ts:103 " +
        s"always emits dot-decimals); offending style values: ${offenders.mkString("[", ", ", "]")}; " +
        s"full markup: $markup"
    )
  }

  test("ISS-1153 red: under Locale.GERMANY a known strut height is emitted in dot-decimal form") {
    val markup = KaTeX.renderToString(formula)
    // Upstream emits e.g. height:0.8141em for the x^2 strut (units.ts:103,
    // +n.toFixed(4)): a fractional em height with a DOT decimal separator.
    val dotDecimalHeightRe = """height:0\.[0-9]+em""".r
    assert(
      dotDecimalHeightRe.findFirstIn(markup).isDefined,
      s"expected a fractional dot-decimal em height (e.g. height:0.8141em) in the markup; markup: $markup"
    )
  }

  test("control: under Locale.US the same render emits dot decimals only") {
    // Pins that the assertions themselves are sound: with a dot-decimal
    // default locale the markup passes both checks today.
    Locale.setDefault(Locale.US)
    val markup = KaTeX.renderToString(formula)
    val styles = styleValues(markup)
    assert(styles.nonEmpty, s"expected style attributes in the markup; markup: $markup")
    val offenders = styles.filter(s => commaDecimalRe.findFirstIn(s).isDefined)
    assert(
      offenders.isEmpty,
      s"under Locale.US no comma decimals may appear; offending style values: " +
        s"${offenders.mkString("[", ", ", "]")}; full markup: $markup"
    )
    val dotDecimalHeightRe = """height:0\.[0-9]+em""".r
    assert(
      dotDecimalHeightRe.findFirstIn(markup).isDefined,
      s"expected a fractional dot-decimal em height in the markup; markup: $markup"
    )
  }
}
