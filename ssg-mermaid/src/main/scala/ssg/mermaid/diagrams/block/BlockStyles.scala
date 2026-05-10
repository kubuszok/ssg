/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/block/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> BlockStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package block

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for block diagram elements. */
object BlockStyles {

  /** Generates CSS rules for all block diagram elements. */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    sb.append(
      s""".blockBox {
         |  fill: ${vars.mainBkg};
         |  stroke: ${vars.nodeBorder};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".blockLabel {
         |  font-size: 14px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.append(
      s""".blockEdge {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 1.5px;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".blockEdgeLabel {
         |  font-size: 11px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
