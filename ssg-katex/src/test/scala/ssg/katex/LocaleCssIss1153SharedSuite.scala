/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1153 (cross-platform invariant): rendered CSS must never
 * contain comma decimal separators, on ANY platform, under the platform's
 * DEFAULT locale.
 *
 * Upstream reference (original-src/katex, v0.16.45), cited per C11:
 *   - src/units.ts:99-104 — `makeEm` returns `+n.toFixed(4) + "em"`; ECMA-262
 *     `Number.prototype.toFixed` / `Number::toString` always use "." as the
 *     decimal separator, so upstream output is locale-independent by
 *     construction.
 *
 * CI-locale dependence note: this suite runs under whatever default locale
 * the host provides. On dot-decimal hosts (e.g. en_US CI) it passes today
 * even without the fix; on JVMs whose default locale is comma-decimal (e.g.
 * de_DE, pl_PL — and the en_PL default of the machine this issue was
 * reproduced on) it is RED today, which is exactly the bug: developer
 * machines with comma locales render invalid CSS. The ISS-1153 fix
 * (locale-independent formatting in Units.makeEm) makes it pass everywhere.
 * Scala.js and Scala Native runs of this suite tell whether those platforms
 * share the bug.
 */
package ssg
package katex

class LocaleCssIss1153SharedSuite extends KaTeXTestSuite {

  // Matches the contents of every style="..." attribute in the markup.
  private val styleAttrRe = """style="([^"]*)"""".r

  // A comma used as a decimal separator between two digits — never valid in
  // CSS numeric values (upstream always emits dot-decimals, units.ts:103).
  private val commaDecimalRe = """[0-9],[0-9]""".r

  test("ISS-1153: style attributes contain no comma decimals under the default locale") {
    val markup = KaTeX.renderToString("x^2")
    val styles = styleAttrRe.findAllMatchIn(markup).map(_.group(1)).toList
    assert(styles.nonEmpty, s"expected style attributes in the markup; markup: $markup")
    val offenders = styles.filter(s => commaDecimalRe.findFirstIn(s).isDefined)
    assert(
      offenders.isEmpty,
      s"style attributes must never contain comma decimal separators (upstream units.ts:103 " +
        s"always emits dot-decimals); offending style values: ${offenders.mkString("[", ", ", "]")}; " +
        s"full markup: $markup"
    )
  }
}
