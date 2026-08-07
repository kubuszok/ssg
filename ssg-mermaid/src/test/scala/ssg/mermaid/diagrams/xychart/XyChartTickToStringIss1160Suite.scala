/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression suite for ISS-1160: XyChart y-tick labels must render the exact
 * tick value (fractional ticks preserved), matching upstream mermaid.
 *
 * Upstream renders each y-axis tick label with `tick.toString()`:
 *   mermaid/packages/mermaid/src/diagrams/xychart/chartBuilder/components/axis/baseAxis.ts:201
 *     text: tick.toString()
 *
 * SSG currently rounds the value to an integer
 * (XyChartRenderer.scala:178 `label.text(Math.round(value).toString)`),
 * so a fractional tick like 2.5 is corrupted to 3 and 7.5 to 8.
 */
package ssg
package mermaid
package diagrams
package xychart

import munit.FunSuite

final class XyChartTickToStringIss1160Suite extends FunSuite {

  // SSG generates 5 evenly-spaced y-ticks:
  //   value = minVal + (i/4.0) * (maxVal - minVal)   for i in 0..4
  // With an explicit y-axis range of [0, 10] the ticks are:
  //   0, 2.5, 5, 7.5, 10
  // The 2.5 and 7.5 ticks are the fractional ones that Math.round corrupts.
  private val source =
    "xychart-beta\n" +
      "y-axis \"y\" 0 --> 10\n" +
      "bar [1, 2, 3]"

  test("y-tick labels preserve fractional values (baseAxis.ts:201 tick.toString())") {
    val svg = XyChartDiagram.render(source)

    // Upstream `tick.toString()` yields "2.5" and "7.5" for the fractional ticks.
    assert(
      svg.contains(">2.5<"),
      s"expected fractional y-tick label >2.5< (upstream tick.toString()) but SVG was:\n$svg"
    )
    assert(
      svg.contains(">7.5<"),
      s"expected fractional y-tick label >7.5< (upstream tick.toString()) but SVG was:\n$svg"
    )
  }
}
