/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/state/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> StateStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package state

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for state diagram elements.
  *
  * Generates CSS rules for state boxes, transitions, labels, and special states.
  */
object StateStyles {

  /** Generates CSS rules for all state diagram elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    // State label
    sb.append(
      s""".stateLabel {
         |  fill: ${vars.stateLabelColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // State box
    sb.append(
      s""".statebox {
         |  fill: ${vars.stateBkg};
         |  stroke: ${vars.nodeBorder};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    // Composite state
    sb.append(
      s""".compositeState {
         |  fill: ${vars.compositeBackground};
         |  stroke: ${vars.compositeBorder};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    // Composite label
    sb.append(
      s""".compositeLabel {
         |  fill: ${vars.stateLabelColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // Transition line
    sb.append(
      s""".transition {
         |  stroke: ${vars.transitionColor};
         |  stroke-width: 1;
         |  fill: none;
         |}
         |""".stripMargin
    )

    // Transition label
    sb.append(
      s""".transitionLabel {
         |  fill: ${vars.transitionLabelColor};
         |  font-family: ${vars.fontFamily};
         |}
         |""".stripMargin
    )

    // Note
    sb.append(
      s""".note {
         |  fill: ${vars.noteBkgColor};
         |  stroke: ${vars.noteBorderColor};
         |  stroke-width: 1px;
         |}
         |.noteText {
         |  fill: ${vars.noteTextColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: 12px;
         |}
         |""".stripMargin
    )

    // Title
    sb.append(
      s""".stateTitleText {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: ${vars.textColor};
         |}
         |""".stripMargin
    )

    // Start/End states
    sb.append(
      s""".start-state {
         |  fill: ${vars.specialStateColor};
         |}
         |.end-state-inner {
         |  fill: ${vars.specialStateColor};
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
