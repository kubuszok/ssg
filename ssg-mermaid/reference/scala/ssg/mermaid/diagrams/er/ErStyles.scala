/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/er/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> ErStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package er

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for ER diagram elements.
  *
  * Generates CSS rules for entity boxes, attribute rows, relationship lines, and labels.
  */
object ErStyles {

  /** Generates CSS rules for all ER diagram elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    // Entity box
    sb.append(
      s""".entityBox {
         |  fill: ${vars.mainBkg};
         |  stroke: ${vars.nodeBorder};
         |}
         |""".stripMargin
    )

    // Entity labels
    sb.append(
      s""".entityLabel {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // Attribute background colors (alternating)
    sb.append(
      s""".attributeBackgroundColorOdd {
         |  fill: ${vars.attributeBackgroundColorOdd};
         |}
         |.attributeBackgroundColorEven {
         |  fill: ${vars.attributeBackgroundColorEven};
         |}
         |""".stripMargin
    )

    // Relationship line
    sb.append(
      s""".relationshipLine {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 1;
         |  fill: none;
         |}
         |""".stripMargin
    )

    // Relationship label
    sb.append(
      s""".relationshipLabel {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // ER title
    sb.append(
      s""".er-title {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
