/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/flowchart/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() → FlowchartStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package flowchart

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for flowchart elements.
  *
  * Generates CSS rules for node classes, edge classes, and label classes based on the current theme variables. These rules are embedded in the SVG `<style>` element.
  */
object FlowchartStyles {

  /** Generates CSS rules for all flowchart elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val nodeTextColor = if (vars.nodeTextColor.nonEmpty) vars.nodeTextColor else vars.textColor

    val sb = new StringBuilder()

    // Label styles
    sb.append(
      s""".label {
         |  font-family: ${vars.fontFamily};
         |  color: $nodeTextColor;
         |}
         |""".stripMargin
    )

    // Cluster label
    sb.append(
      s""".cluster-label text {
         |  fill: ${vars.titleColor};
         |}
         |.cluster-label span {
         |  color: ${vars.titleColor};
         |}
         |""".stripMargin
    )

    // Label text fills
    sb.append(
      s""".label text, span {
         |  fill: $nodeTextColor;
         |  color: $nodeTextColor;
         |}
         |""".stripMargin
    )

    // Node shapes
    sb.append(
      s""".node rect,
         |.node circle,
         |.node ellipse,
         |.node polygon,
         |.node path {
         |  fill: ${vars.mainBkg};
         |  stroke: ${vars.nodeBorder};
         |  stroke-width: 1px;
         |}
         |""".stripMargin
    )

    // Node label alignment
    sb.append(
      """.node .label text {
        |  text-anchor: middle;
        |}
        |.node .label {
        |  text-align: center;
        |}
        |.node.clickable {
        |  cursor: pointer;
        |}
        |""".stripMargin
    )

    // Arrowhead
    sb.append(
      s""".arrowheadPath {
         |  fill: ${vars.arrowheadColor};
         |}
         |""".stripMargin
    )

    // Edge paths
    sb.append(
      s""".edgePath .path {
         |  stroke: ${vars.lineColor};
         |  stroke-width: 2.0px;
         |}
         |""".stripMargin
    )

    // Flowchart link
    sb.append(
      s""".flowchart-link {
         |  stroke: ${vars.lineColor};
         |  fill: none;
         |}
         |""".stripMargin
    )

    // Edge labels
    sb.append(
      s""".edgeLabel {
         |  background-color: ${vars.edgeLabelBackground};
         |  text-align: center;
         |}
         |.edgeLabel rect {
         |  opacity: 0.5;
         |  background-color: ${vars.edgeLabelBackground};
         |  fill: ${vars.edgeLabelBackground};
         |}
         |""".stripMargin
    )

    // Clusters
    sb.append(
      s""".cluster rect {
         |  fill: ${vars.clusterBkg};
         |  stroke: ${vars.clusterBorder};
         |  stroke-width: 1px;
         |}
         |.cluster text {
         |  fill: ${vars.titleColor};
         |}
         |.cluster span {
         |  color: ${vars.titleColor};
         |}
         |""".stripMargin
    )

    // Title
    sb.append(
      s""".flowchartTitleText {
         |  text-anchor: middle;
         |  font-size: 18px;
         |  fill: ${vars.textColor};
         |}
         |""".stripMargin
    )

    // Tooltip
    sb.append(
      s""".node .tooltip {
         |  position: absolute;
         |  padding: 8px;
         |  background-color: ${vars.secondBkg};
         |  border: 1px solid ${vars.border2};
         |  border-radius: 2px;
         |  font-family: ${vars.fontFamily};
         |  font-size: 12px;
         |  color: ${vars.textColor};
         |  z-index: 100;
         |}
         |""".stripMargin
    )

    // Label background for edge labels
    sb.append(
      s""".labelBkg {
         |  background-color: ${vars.edgeLabelBackground};
         |  fill: ${vars.edgeLabelBackground};
         |}
         |""".stripMargin
    )

    // Rough-node styles (for hand-drawn/sketch mode)
    sb.append(
      s""".node .rough-node {
         |  fill: ${vars.mainBkg};
         |  stroke: ${vars.nodeBorder};
         |  stroke-width: 1px;
         |}
         |.node .rough-node .label {
         |  text-anchor: middle;
         |}
         |""".stripMargin
    )

    sb.toString
  }

  /** Returns CSS class names for a node based on its index in the color scale.
    *
    * @param index
    *   the color scale index (0-based)
    * @return
    *   CSS class string
    */
  def nodeClass(index: Int): String =
    s"node default node-$index"

  /** Returns CSS class names for an edge based on its stroke type.
    *
    * @param stroke
    *   stroke type: "normal", "thick", "dotted", "invisible"
    * @return
    *   CSS class string
    */
  def edgeClass(stroke: String): String = {
    val thicknessClass = s"edge-thickness-$stroke"
    val patternClass   = stroke match {
      case "dotted"    => "edge-pattern-dotted"
      case "invisible" => "edge-pattern-invisible"
      case "thick"     => "edge-pattern-solid"
      case _           => "edge-pattern-solid"
    }
    s"$thicknessClass $patternClass flowchart-link"
  }
}
