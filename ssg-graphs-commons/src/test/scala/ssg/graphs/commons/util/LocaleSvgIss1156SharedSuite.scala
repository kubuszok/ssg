/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1156 (cross-platform invariant): the shared SVG number
 * formatters must produce dot-decimal output on ANY platform, under the
 * platform's DEFAULT locale. Companion to LocaleSvgIss1156JvmSuite (which
 * forces Locale.GERMANY — a JVM-only facility).
 *
 * Offending port sites:
 *   - ssg-graphs-commons/src/main/scala/ssg/graphs/commons/util/FormatUtil.scala:24
 *     — `f"$rounded%.4f"` in the scientific-notation branch of `formatNumber`.
 *   - ssg-graphs-commons/src/main/scala/ssg/graphs/commons/svg/PathData.scala:206
 *     — `private def fmt` uses `f"$rounded%.2f"`.
 *
 * Expected values (cited per C11): this infrastructure ports/replaces
 * Mermaid JS code — FormatUtil.roundNumber ports
 * original-src/mermaid/packages/mermaid/src/utils.ts:329-332 and PathData
 * replaces d3-path — and ECMA-262 number stringification always uses "."
 * as the decimal separator, so upstream output is locale-independent by
 * construction. SVG 1.1 §4.2 <number> admits only ".".
 *
 * CI-locale dependence note (same shape as LocaleCssIss1153SharedSuite):
 * this suite runs under whatever default locale the host provides. On
 * dot-decimal hosts it passes today even without the fix; on JVMs whose
 * default locale is comma-decimal (de_DE, pl_PL, ...) it is RED today,
 * which is exactly the bug. Scala.js and Scala Native runs tell whether
 * those platforms share the bug.
 */
package ssg
package graphs
package commons
package util

import munit.FunSuite

import ssg.graphs.commons.svg.PathData

class LocaleSvgIss1156SharedSuite extends FunSuite {

  test("ISS-1156: FormatUtil.formatNumber emits dot decimals under the default locale") {
    assertEquals(FormatUtil.formatNumber(0.0001), "0.0001")
    assertEquals(FormatUtil.formatNumber(12345678.5), "12345678.5")
  }

  test("ISS-1156: PathData emits dot-decimal coordinates under the default locale") {
    assertEquals(PathData().moveTo(1.5, 2.25).lineTo(3.75, 4.5).toString, "M1.5,2.25 L3.75,4.5")
  }
}
