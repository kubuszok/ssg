/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/mindmap/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> MindmapStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package mindmap

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for mindmap diagram elements. */
object MindmapStyles {

  /** Generates CSS rules for all mindmap diagram elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    // Node shape
    sb.append(
      s""".mindmap-shape {
         |  stroke: ${vars.nodeBorder};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    // Node text
    sb.append(
      s""".mindmap-text {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 14px;
         |}
         |""".stripMargin
    )

    // Edge
    sb.append(
      s""".mindmap-edge {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 1.5;
         |  fill: none;
         |}
         |""".stripMargin
    )

    // Level-based colors using cScale
    for (i <- 0 until vars.THEME_COLOR_LIMIT)
      if (vars.cScale(i).nonEmpty) {
        sb.append(
          s""".mindmap-level-$i > .mindmap-shape {
             |  fill: ${vars.cScale(i)};
             |}
             |""".stripMargin
        )
      }

    sb.toString
  }
}
