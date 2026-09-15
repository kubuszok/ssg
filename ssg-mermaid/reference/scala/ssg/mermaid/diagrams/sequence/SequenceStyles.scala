/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sequence/styles.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() → SequenceStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sequence

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for sequence diagram elements.
  *
  * Generates CSS rules for actor, message, note, and loop classes based on the current theme variables. These rules are embedded in the SVG `<style>` element.
  *
  * Ports `getStyles()` from `styles.js`.
  */
object SequenceStyles {

  /** Generates CSS rules for all sequence diagram elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val actorBorder           = vars.actorBorder
    val actorBkg              = vars.actorBkg
    val actorTextColor        = if (vars.actorTextColor.nonEmpty) vars.actorTextColor else vars.textColor
    val actorLineColor        = if (vars.actorLineColor.nonEmpty) vars.actorLineColor else vars.lineColor
    val signalColor           = if (vars.signalColor.nonEmpty) vars.signalColor else vars.lineColor
    val signalTextColor       = if (vars.signalTextColor.nonEmpty) vars.signalTextColor else vars.textColor
    val labelBoxBorderColor   = if (vars.labelBoxBorderColor.nonEmpty) vars.labelBoxBorderColor else vars.nodeBorder
    val labelBoxBkgColor      = if (vars.labelBoxBkgColor.nonEmpty) vars.labelBoxBkgColor else vars.mainBkg
    val labelTextColor        = if (vars.labelTextColor.nonEmpty) vars.labelTextColor else vars.textColor
    val loopTextColor         = if (vars.loopTextColor.nonEmpty) vars.loopTextColor else vars.textColor
    val noteBorderColor       = if (vars.noteBorderColor.nonEmpty) vars.noteBorderColor else "#aaaa33"
    val noteBkgColor          = if (vars.noteBkgColor.nonEmpty) vars.noteBkgColor else "#fff5ad"
    val noteTextColor         = if (vars.noteTextColor.nonEmpty) vars.noteTextColor else vars.textColor
    val activationBkgColor    = if (vars.activationBkgColor.nonEmpty) vars.activationBkgColor else "#f4f4f4"
    val activationBorderColor = if (vars.activationBorderColor.nonEmpty) vars.activationBorderColor else "#666"
    val sequenceNumberColor   = if (vars.sequenceNumberColor.nonEmpty) vars.sequenceNumberColor else "#fff"

    val sb = new StringBuilder()

    // Actor styles
    sb.append(
      s""".actor {
         |  stroke: $actorBorder;
         |  fill: $actorBkg;
         |}
         |""".stripMargin
    )

    sb.append(
      s"""text.actor > tspan {
         |  fill: $actorTextColor;
         |  stroke: none;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".actor-line {
         |  stroke: $actorLineColor;
         |}
         |""".stripMargin
    )

    // Message line styles
    sb.append(
      s""".messageLine0 {
         |  stroke-width: 1.5;
         |  stroke-dasharray: none;
         |  stroke: $signalColor;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".messageLine1 {
         |  stroke-width: 1.5;
         |  stroke-dasharray: 2, 2;
         |  stroke: $signalColor;
         |}
         |""".stripMargin
    )

    // Arrowhead styles
    sb.append(
      s"""#arrowhead path {
         |  fill: $signalColor;
         |  stroke: $signalColor;
         |}
         |""".stripMargin
    )

    // Sequence number
    sb.append(
      s""".sequenceNumber {
         |  fill: $sequenceNumberColor;
         |}
         |""".stripMargin
    )

    sb.append(
      s"""#sequencenumber {
         |  fill: $signalColor;
         |}
         |""".stripMargin
    )

    // Crosshead
    sb.append(
      s"""#crosshead path {
         |  fill: $signalColor;
         |  stroke: $signalColor;
         |}
         |""".stripMargin
    )

    // Message text
    sb.append(
      s""".messageText {
         |  fill: $signalTextColor;
         |  stroke: none;
         |}
         |""".stripMargin
    )

    // Label box
    sb.append(
      s""".labelBox {
         |  stroke: $labelBoxBorderColor;
         |  fill: $labelBoxBkgColor;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".labelText, .labelText > tspan {
         |  fill: $labelTextColor;
         |  stroke: none;
         |}
         |""".stripMargin
    )

    // Loop text
    sb.append(
      s""".loopText, .loopText > tspan {
         |  fill: $loopTextColor;
         |  stroke: none;
         |}
         |""".stripMargin
    )

    // Loop line
    sb.append(
      s""".loopLine {
         |  stroke-width: 2px;
         |  stroke-dasharray: 2, 2;
         |  stroke: $labelBoxBorderColor;
         |  fill: $labelBoxBorderColor;
         |}
         |""".stripMargin
    )

    // Note styles
    // stroke: #decc93;
    sb.append(
      s""".note {
         |  stroke: $noteBorderColor;
         |  fill: $noteBkgColor;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".noteText, .noteText > tspan {
         |  fill: $noteTextColor;
         |  stroke: none;
         |}
         |""".stripMargin
    )

    // Activation styles
    sb.append(
      s""".activation0 {
         |  fill: $activationBkgColor;
         |  stroke: $activationBorderColor;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".activation1 {
         |  fill: $activationBkgColor;
         |  stroke: $activationBorderColor;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".activation2 {
         |  fill: $activationBkgColor;
         |  stroke: $activationBorderColor;
         |}
         |""".stripMargin
    )

    // Actor popup menu
    sb.append(""".actorPopupMenu {
                |  position: absolute;
                |}
                |""".stripMargin)

    sb.append(
      s""".actorPopupMenuPanel {
         |  position: absolute;
         |  fill: $actorBkg;
         |  box-shadow: 0px 8px 16px 0px rgba(0,0,0,0.2);
         |  filter: drop-shadow(3px 5px 2px rgb(0 0 0 / 0.4));
         |}
         |""".stripMargin
    )

    // Actor man (stick figure) styles
    sb.append(
      s""".actor-man line {
         |  stroke: $actorBorder;
         |  fill: $actorBkg;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".actor-man circle, line {
         |  stroke: $actorBorder;
         |  fill: $actorBkg;
         |  stroke-width: 2px;
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
