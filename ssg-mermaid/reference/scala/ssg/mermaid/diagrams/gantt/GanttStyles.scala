/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/gantt/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> GanttStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package gantt

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for Gantt chart elements.
  *
  * Generates CSS rules for task bars, sections, labels, and grid lines.
  */
object GanttStyles {

  /** Generates CSS rules for all Gantt chart elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    // Task bars
    sb.append(
      s""".task {
         |  fill: ${vars.taskBkgColor};
         |  stroke: ${vars.taskBorderColor};
         |}
         |""".stripMargin
    )

    // Task text
    sb.append(
      s""".taskText {
         |  fill: ${vars.taskTextColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: ${vars.fontSize};
         |}
         |""".stripMargin
    )

    // Active task
    sb.append(
      s""".active {
         |  fill: ${vars.activeTaskBkgColor};
         |  stroke: ${vars.activeTaskBorderColor};
         |}
         |""".stripMargin
    )

    // Done task
    sb.append(
      s""".done {
         |  fill: ${vars.doneTaskBkgColor};
         |  stroke: ${vars.doneTaskBorderColor};
         |}
         |""".stripMargin
    )

    // Critical task
    sb.append(
      s""".crit {
         |  fill: ${vars.critBkgColor};
         |  stroke: ${vars.critBorderColor};
         |}
         |""".stripMargin
    )

    // Milestone
    sb.append(""".milestone {
                |  transform: rotate(45deg);
                |}
                |""".stripMargin)

    // Section backgrounds (alternating)
    sb.append(
      s""".section0 {
         |  fill: ${vars.sectionBkgColor};
         |}
         |.section1 {
         |  fill: ${vars.altSectionBkgColor};
         |}
         |""".stripMargin
    )

    // Section title
    sb.append(
      s""".sectionTitle {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 11px;
         |  font-weight: bold;
         |}
         |""".stripMargin
    )

    // Title
    sb.append(
      s""".titleText {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // Grid
    sb.append(
      s""".grid {
         |  stroke: ${vars.gridColor};
         |  stroke-width: 0.5;
         |}
         |""".stripMargin
    )

    // Axis labels
    sb.append(
      s""".axisLabel {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 10px;
         |}
         |""".stripMargin
    )

    // Today marker
    sb.append(
      s""".today {
         |  stroke: ${vars.todayLineColor};
         |  stroke-width: 2;
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
