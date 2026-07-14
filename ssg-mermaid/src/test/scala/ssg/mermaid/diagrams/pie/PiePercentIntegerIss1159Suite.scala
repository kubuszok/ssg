/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1159: SSG pie percentage labels render with one decimal
 * place instead of the integer percentages produced by upstream Mermaid.
 *
 * Upstream pie slice labels round to zero fraction digits:
 *   original-src/mermaid/packages/mermaid/src/diagrams/pie/pieRenderer.ts:119
 *     — `((datum.data.value / sum) * 100).toFixed(0) + '%'`
 * so a 1/3 slice renders "33%" and a 2/3 slice renders "67%".
 *
 * The port instead formats with `FormatUtil.toFixed(percentage, 1)`:
 *   ssg-mermaid/src/main/scala/ssg/mermaid/diagrams/pie/PieRenderer.scala:153
 * which renders "33.3%" / "66.7%" — one decimal place, diverging from
 * upstream's integer percentages.
 *
 * Cross-platform (src/test/scala): pie rendering exercises no JVM-only
 * facility, so the divergence reproduces identically on JVM, JS, and Native.
 */
package ssg
package mermaid
package diagrams
package pie

import munit.FunSuite

class PiePercentIntegerIss1159Suite extends FunSuite {

  // A pie chart with fractional slice percentages: 1/3 → 33.333% and
  // 2/3 → 66.667%, so the toFixed(0) vs toFixed(1) difference is observable
  // (integer "33%"/"67%" upstream vs one-decimal "33.3%"/"66.7%" in SSG).
  private val pieInput = "pie\n    \"a\" : 1\n    \"b\" : 2"

  // A digit, a dot, a digit, then '%' — the one-decimal percentage form the
  // port currently emits (e.g. "33.3%"). Upstream never produces this: its
  // labels round to zero fraction digits (pieRenderer.ts:119 toFixed(0)).
  private val decimalPercentRe = """[0-9]\.[0-9]%""".r

  test("ISS-1159 red: pie percentage labels are integers per pieRenderer.ts:119 toFixed(0)") {
    val svg = Mermaid.render(pieInput)
    // Upstream: ((value / sum) * 100).toFixed(0) + '%' — pieRenderer.ts:119.
    // 1/3 of the pie → "33%", 2/3 → "67%".
    assert(
      svg.contains("33%"),
      s"expected integer percentage label '33%%' for the 1/3 slice (upstream " +
        s"pieRenderer.ts:119 toFixed(0)); svg: $svg"
    )
    assert(
      svg.contains("67%"),
      s"expected integer percentage label '67%%' for the 2/3 slice (upstream " +
        s"pieRenderer.ts:119 toFixed(0)); svg: $svg"
    )
    // The port must NOT emit the one-decimal forms "33.3%" / "66.7%".
    assertEquals(
      decimalPercentRe.findFirstIn(svg),
      None,
      s"pie percentage labels must not carry a decimal place (upstream rounds to " +
        s"zero fraction digits, pieRenderer.ts:119); svg: $svg"
    )
  }
}
