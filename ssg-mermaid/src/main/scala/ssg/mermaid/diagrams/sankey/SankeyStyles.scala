/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sankey/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> SankeyStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sankey

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for Sankey diagram elements. */
object SankeyStyles {

  /** Generates CSS rules for all Sankey diagram elements. */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    sb.append(
      s""".sankeyNode {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 0.5px;
         |}
         |""".stripMargin
    )

    sb.append(s""".sankeyFlow {
                 |  stroke: none;
                 |}
                 |""".stripMargin)

    sb.append(
      s""".sankeyLabel {
         |  font-size: 12px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
