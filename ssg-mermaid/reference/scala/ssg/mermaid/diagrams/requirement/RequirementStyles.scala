/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/requirement/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> RequirementStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package requirement

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for requirement diagram elements. */
object RequirementStyles {

  /** Generates CSS rules for all requirement diagram elements. */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    sb.append(
      s""".reqBox {
         |  fill: ${vars.mainBkg};
         |  stroke: ${vars.border1};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".elemBox {
         |  fill: ${vars.secondBkg};
         |  stroke: ${vars.border2};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".reqTypeLabel {
         |  font-size: 10px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-style: italic;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".reqNameLabel {
         |  font-size: 14px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-weight: bold;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".reqInfoLabel {
         |  font-size: 10px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.append(
      s""".reqRelLine {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 1.5px;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".reqRelLabel {
         |  font-size: 10px;
         |  fill: ${vars.lineColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
