/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/flowchart/styles.ts (+ other diagram style modules)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces per-diagram style template functions with a single CSS generator
 *   Idiom: String interpolation for CSS generation; pure function from ThemeVariables to String
 *   Renames: getStyles() → CssGenerator.generateFlowchartStyles()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package theme

/** Generates CSS style blocks from [[ThemeVariables]] for embedding in SVG output.
  *
  * Each diagram type has its own CSS generation method. The generated CSS uses theme variable values for all colors, ensuring consistent theming.
  */
object CssGenerator {

  /** Generates the complete CSS `<style>` content for a flowchart diagram.
    *
    * Ports the `styles.ts` `getStyles()` function from the flowchart diagram.
    *
    * @param vars
    *   the theme variables to use for color values
    * @return
    *   CSS rules as a string
    */
  def generateFlowchartStyles(vars: ThemeVariables): String = {
    val nodeTextColor = if (vars.nodeTextColor.nonEmpty) vars.nodeTextColor else vars.textColor

    s""".label {
       |  font-family: ${vars.fontFamily};
       |  color: $nodeTextColor;
       |}
       |.cluster-label text {
       |  fill: ${vars.titleColor};
       |}
       |.cluster-label span {
       |  color: ${vars.titleColor};
       |}
       |.label text, span {
       |  fill: $nodeTextColor;
       |  color: $nodeTextColor;
       |}
       |.node rect,
       |.node circle,
       |.node ellipse,
       |.node polygon,
       |.node path {
       |  fill: ${vars.mainBkg};
       |  stroke: ${vars.nodeBorder};
       |  stroke-width: 1px;
       |}
       |.node .label text {
       |  text-anchor: middle;
       |}
       |.node .label {
       |  text-align: center;
       |}
       |.node.clickable {
       |  cursor: pointer;
       |}
       |.arrowheadPath {
       |  fill: ${vars.arrowheadColor};
       |}
       |.edgePath .path {
       |  stroke: ${vars.lineColor};
       |  stroke-width: 2.0px;
       |}
       |.flowchart-link {
       |  stroke: ${vars.lineColor};
       |  fill: none;
       |}
       |.edgeLabel {
       |  background-color: ${vars.edgeLabelBackground};
       |  text-align: center;
       |}
       |.edgeLabel rect {
       |  opacity: 0.5;
       |  background-color: ${vars.edgeLabelBackground};
       |  fill: ${vars.edgeLabelBackground};
       |}
       |.cluster rect {
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
       |.flowchartTitleText {
       |  text-anchor: middle;
       |  font-size: 18px;
       |  fill: ${vars.textColor};
       |}""".stripMargin
  }

  /** Generates base Mermaid SVG styles (applied to all diagram types).
    *
    * @param vars
    *   the theme variables
    * @return
    *   CSS rules for the base styles
    */
  def generateBaseStyles(vars: ThemeVariables): String =
    s"""text {
       |  font-family: ${vars.fontFamily};
       |  font-size: ${vars.fontSize};
       |}
       |.mermaid-main-font {
       |  font-family: ${vars.fontFamily};
       |}""".stripMargin

  /** Generates a complete `<style>` block suitable for embedding in an SVG `<defs>` section.
    *
    * @param vars
    *   the theme variables
    * @param diagramCss
    *   diagram-specific CSS (e.g. from [[generateFlowchartStyles]])
    * @param userCss
    *   additional user-provided CSS (from config.themeCSS)
    * @return
    *   complete CSS content wrapped in a CDATA section
    */
  def wrapInStyleElement(vars: ThemeVariables, diagramCss: String, userCss: String = ""): String = {
    val baseCss = generateBaseStyles(vars)
    val allCss  = new StringBuilder()
    allCss.append(baseCss)
    allCss.append("\n")
    allCss.append(diagramCss)
    if (userCss.nonEmpty) {
      allCss.append("\n")
      allCss.append(userCss)
    }
    allCss.toString
  }
}
