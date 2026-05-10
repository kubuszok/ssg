/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/class/styles.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() → ClassStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package class_

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for class diagram elements.
  *
  * Generates CSS rules for class nodes, relations, notes, and labels based on the current theme variables. These rules are embedded in the SVG `<style>` element.
  *
  * Ports `getStyles()` from `styles.js`.
  */
object ClassStyles {

  /** Generates CSS rules for all class diagram elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val nodeBorder = vars.nodeBorder
    val classText  = if (vars.classText.nonEmpty) vars.classText else vars.textColor
    val mainBkg    = vars.mainBkg
    val lineColor  = vars.lineColor
    val textColor  = vars.textColor
    val fontFamily = vars.fontFamily

    val sb = new StringBuilder()

    // Class group text
    sb.append(
      s"""g.classGroup text {
         |  fill: ${if (nodeBorder.nonEmpty) nodeBorder else classText};
         |  stroke: none;
         |  font-family: $fontFamily;
         |  font-size: 10px;
         |}
         |""".stripMargin
    )

    sb.append(s""".title {
                 |  font-weight: bolder;
                 |}
                 |""".stripMargin)

    // Node and edge labels
    sb.append(
      s""".nodeLabel, .edgeLabel {
         |  color: $classText;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".edgeLabel .label rect {
         |  fill: $mainBkg;
         |}
         |""".stripMargin
    )

    sb.append(s""".label text {
                 |  fill: $classText;
                 |}
                 |""".stripMargin)

    sb.append(
      s""".edgeLabel .label span {
         |  background: $mainBkg;
         |}
         |""".stripMargin
    )

    // Class title
    sb.append(""".classTitle {
                |  font-weight: bolder;
                |}
                |""".stripMargin)

    // Node shapes
    sb.append(
      s""".node rect,
         |.node circle,
         |.node ellipse,
         |.node polygon,
         |.node path {
         |  fill: $mainBkg;
         |  stroke: $nodeBorder;
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    // Divider
    sb.append(
      s""".divider {
         |  stroke: $nodeBorder;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    // Clickable
    sb.append("""g.clickable {
                |  cursor: pointer;
                |}
                |""".stripMargin)

    // Class group rect
    sb.append(
      s"""g.classGroup rect {
         |  fill: $mainBkg;
         |  stroke: $nodeBorder;
         |}
         |""".stripMargin
    )

    sb.append(
      s"""g.classGroup line {
         |  stroke: $nodeBorder;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    // Class label box
    sb.append(
      s""".classLabel .box {
         |  stroke: none;
         |  stroke-width: 0;
         |  fill: $mainBkg;
         |  opacity: 0.5;
         |}
         |""".stripMargin
    )

    sb.append(
      s""".classLabel .label {
         |  fill: $nodeBorder;
         |  font-size: 10px;
         |}
         |""".stripMargin
    )

    // Relation line
    sb.append(
      s""".relation {
         |  stroke: $lineColor;
         |  stroke-width: 1;
         |  fill: none;
         |}
         |""".stripMargin
    )

    // Dashed and dotted lines
    sb.append(""".dashed-line {
                |  stroke-dasharray: 3;
                |}
                |""".stripMargin)

    sb.append(""".dotted-line {
                |  stroke-dasharray: 1 2;
                |}
                |""".stripMargin)

    // Composition markers
    sb.append(
      s"""#compositionStart, .composition {
         |  fill: $lineColor !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    sb.append(
      s"""#compositionEnd, .composition {
         |  fill: $lineColor !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    // Dependency markers
    sb.append(
      s"""#dependencyStart, .dependency {
         |  fill: $lineColor !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    // Extension markers
    sb.append(
      s"""#extensionStart, .extension {
         |  fill: transparent !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    sb.append(
      s"""#extensionEnd, .extension {
         |  fill: transparent !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    // Aggregation markers
    sb.append(
      s"""#aggregationStart, .aggregation {
         |  fill: transparent !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    sb.append(
      s"""#aggregationEnd, .aggregation {
         |  fill: transparent !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    // Lollipop markers
    sb.append(
      s"""#lollipopStart, .lollipop {
         |  fill: $mainBkg !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    sb.append(
      s"""#lollipopEnd, .lollipop {
         |  fill: $mainBkg !important;
         |  stroke: $lineColor !important;
         |  stroke-width: 1;
         |}
         |""".stripMargin
    )

    // Edge terminals
    sb.append(
      """.edgeTerminals {
        |  font-size: 11px;
        |  line-height: initial;
        |}
        |""".stripMargin
    )

    // Class title text
    sb.append(
      s""".classTitleText {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: $textColor;
         |}
         |""".stripMargin
    )

    sb.toString
  }
}
