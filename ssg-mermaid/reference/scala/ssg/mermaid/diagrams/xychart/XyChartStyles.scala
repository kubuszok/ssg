/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/xychart/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> XyChartStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package xychart

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for XY chart elements. */
object XyChartStyles {

  /** Generates CSS rules for all XY chart elements. */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    sb.append(
      s""".xychartTitleText {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.append(
      s""".xychartAxisLabel {
         |  font-size: 14px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.append(
      s""".xychartTickLabel {
         |  font-size: 12px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.append(
      s""".xychartAxis {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".xychartTick {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    sb.append(s""".xychartBar {
                 |  opacity: 0.9;
                 |}
                 |""".stripMargin)

    sb.append(s""".xychartLine {
                 |  stroke-width: 2px;
                 |}
                 |""".stripMargin)

    sb.append(
      s""".xychartLinePoint {
         |  stroke: ${vars.background};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
