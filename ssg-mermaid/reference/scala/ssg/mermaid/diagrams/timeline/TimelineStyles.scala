/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/timeline/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> TimelineStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package timeline

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for timeline diagram elements. */
object TimelineStyles {

  /** Generates CSS rules for all timeline diagram elements.
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
      s""".timelineTitleText {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // Timeline line
    sb.append(
      s""".timelineLine {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 2;
         |}
         |""".stripMargin
    )

    // Period labels
    sb.append(
      s""".timelinePeriod {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 14px;
         |}
         |""".stripMargin
    )

    // Section headers
    sb.append(
      s""".timelineSection {
         |  fill: ${vars.titleColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 14px;
         |}
         |""".stripMargin
    )

    // Event boxes
    sb.append(
      s""".timelineEvent {
         |  fill: ${vars.mainBkg};
         |  stroke: ${vars.nodeBorder};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    // Event text
    sb.append(
      s""".timelineEventText {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 12px;
         |}
         |""".stripMargin
    )

    // Fill type classes (for visual variety)
    for (i <- 0 until 8) {
      val fillColor = i match {
        case 0 => vars.fillType0
        case 1 => vars.fillType1
        case 2 => vars.fillType2
        case 3 => vars.fillType3
        case 4 => vars.fillType4
        case 5 => vars.fillType5
        case 6 => vars.fillType6
        case 7 => vars.fillType7
        case _ => vars.mainBkg
      }
      if (fillColor.nonEmpty) {
        sb.append(
          s""".event-fill-$i {
             |  fill: $fillColor;
             |}
             |""".stripMargin
        )
      }
    }

    // Dot
    sb.append(
      s""".timelineDot {
         |  fill: ${vars.lineColor};
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
