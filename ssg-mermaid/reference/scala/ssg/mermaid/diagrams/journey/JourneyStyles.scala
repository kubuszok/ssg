/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/user-journey/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> JourneyStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package journey

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for user journey diagram elements. */
object JourneyStyles {

  /** Generates CSS rules for all journey diagram elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    // Title
    sb.append(
      s""".journeyTitle {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // Section label
    sb.append(
      s""".journeySection {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 14px;
         |  font-weight: bold;
         |}
         |""".stripMargin
    )

    // Section backgrounds (alternating)
    sb.append(
      s""".section-0 {
         |  fill: ${vars.fillType0};
         |  opacity: 0.2;
         |}
         |.section-1 {
         |  fill: ${vars.fillType1};
         |  opacity: 0.2;
         |}
         |""".stripMargin
    )

    // Task box
    sb.append(
      s""".journeyTask {
         |  stroke: ${vars.nodeBorder};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    // Task text
    sb.append(
      s""".journeyTaskText {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 12px;
         |}
         |""".stripMargin
    )

    // Score
    sb.append(
      s""".journeyScore {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // Actor label
    sb.append(
      s""".journeyActor {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
