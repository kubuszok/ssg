/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/quadrant-chart/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> QuadrantStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package quadrant

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for quadrant chart elements. */
object QuadrantStyles {

  /** Generates CSS rules for all quadrant chart elements. */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    sb.append(
      s""".quadrantTitleText {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.append(
      s""".quadrantLabel {
         |  font-size: 14px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  opacity: 0.5;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".quadrantAxisLabel {
         |  font-size: 12px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.append(
      s""".quadrantAxis {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    sb.append(s""".quadrantArea {
                 |  stroke-width: 1px;
                 |}
                 |""".stripMargin)

    sb.append(
      s""".quadrantPoint {
         |  fill: ${vars.primaryColor};
         |  stroke: ${vars.primaryBorderColor};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".quadrantPointLabel {
         |  font-size: 12px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
