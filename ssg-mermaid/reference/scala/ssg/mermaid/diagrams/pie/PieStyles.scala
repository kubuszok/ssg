/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/pie/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> PieStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package pie

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for pie chart elements.
  *
  * Generates CSS rules for pie slices, labels, and legend based on the current theme variables.
  */
object PieStyles {

  /** Generates CSS rules for all pie chart elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    // Pie title
    sb.append(
      s""".pieTitleText {
         |  text-anchor: middle;
         |  font-size: ${vars.pieTitleTextSize};
         |  fill: ${vars.pieTitleTextColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // Slice labels
    sb.append(
      s""".slice {
         |  font-family: ${vars.fontFamily};
         |  fill: ${vars.pieSectionTextColor};
         |  font-size: ${vars.pieSectionTextSize};
         |}
         |""".stripMargin
    )

    // Legend text
    sb.append(
      s""".legend text {
         |  fill: ${vars.pieLegendTextColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: ${vars.pieLegendTextSize};
         |}
         |""".stripMargin
    )

    // Pie circle (slices)
    sb.append(
      s""".pieCircle {
         |  stroke: ${vars.pieStrokeColor};
         |  stroke-width: ${vars.pieStrokeWidth};
         |  opacity: ${vars.pieOpacity};
         |}
         |""".stripMargin
    )

    // Outer stroke
    sb.append(
      s""".pieOuterCircle {
         |  stroke: ${vars.pieOuterStrokeColor};
         |  stroke-width: ${vars.pieOuterStrokeWidth};
         |  fill: none;
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
