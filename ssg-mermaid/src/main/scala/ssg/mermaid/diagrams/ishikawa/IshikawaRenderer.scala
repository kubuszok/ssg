/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid ishikawa (fishbone) diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package ishikawa

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders an Ishikawa (fishbone/cause-and-effect) diagram to SVG. */
object IshikawaRenderer {

  private val Padding:      Double = 30.0
  private val SpineLength:  Double = 600.0
  private val BranchLength: Double = 120.0
  private val CauseSpacing: Double = 25.0

  def render(db: IshikawaDb, config: MermaidConfig): String = {
    val branchCount = db.branches.size.max(1)
    val maxCauses   = if (db.branches.isEmpty) 0 else db.branches.map(_.causes.size).max
    val svgWidth    = SpineLength + Padding * 3 + 100
    val svgHeight   = BranchLength * 2 + maxCauses * CauseSpacing + Padding * 2 + 60

    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)
    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "ishikawa", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = IshikawaStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    // Arrow marker
    val marker = defs.append("marker")
    marker.attr("id", "fishhead").attr("viewBox", "0 0 10 10")
    marker.attr("refX", 10).attr("refY", 5).attr("markerWidth", 8).attr("markerHeight", 8).attr("orient", "auto")
    marker.append("path").attr("d", "M 0 0 L 10 5 L 0 10 z").style("fill", themeVars.lineColor)

    val mainGroup   = svg.append("g")
    val spineY      = svgHeight / 2
    val spineStartX = Padding
    val spineEndX   = SpineLength + Padding

    // Draw spine (main horizontal line)
    val spine = mainGroup.append("line")
    spine.attr("x1", spineStartX).attr("y1", spineY)
    spine.attr("x2", spineEndX).attr("y2", spineY)
    spine.attr("marker-end", "url(#fishhead)").classed("ishikawaSpine", true)

    // Effect label (at the head/right end)
    if (db.effect.nonEmpty) {
      mainGroup.append("text").attr("x", spineEndX + 15).attr("y", spineY + 5).attr("text-anchor", "start").classed("ishikawaEffect", true).text(db.effect)
    }

    // Draw branches (alternating top/bottom)
    val spacing = if (branchCount > 1) (SpineLength - 60) / (branchCount - 1).toDouble else SpineLength / 2
    for ((branch, idx) <- db.branches.zipWithIndex) {
      val branchX    = spineStartX + 30 + spacing * idx
      val isTop      = idx % 2 == 0
      val branchEndY = if (isTop) spineY - BranchLength else spineY + BranchLength

      // Branch line
      val line = mainGroup.append("line")
      line.attr("x1", branchX).attr("y1", spineY)
      line.attr("x2", branchX).attr("y2", branchEndY)
      line.classed("ishikawaBranch", true)

      // Branch label
      val labelY = if (isTop) branchEndY - 10 else branchEndY + 20
      mainGroup.append("text").attr("x", branchX).attr("y", labelY).attr("text-anchor", "middle").classed("ishikawaBranchLabel", true).text(branch.label)

      // Causes (sub-branches)
      for ((cause, cIdx) <- branch.causes.zipWithIndex) {
        val causeY = if (isTop) {
          branchEndY + 20 + cIdx * CauseSpacing
        } else {
          branchEndY - 20 - cIdx * CauseSpacing
        }
        val causeEndX = branchX + 80

        val causeLine = mainGroup.append("line")
        causeLine.attr("x1", branchX).attr("y1", causeY)
        causeLine.attr("x2", causeEndX).attr("y2", causeY)
        causeLine.classed("ishikawaCause", true)

        mainGroup.append("text").attr("x", causeEndX + 5).attr("y", causeY + 4).attr("text-anchor", "start").classed("ishikawaCauseLabel", true).text(cause)
      }
    }

    svg.build().toMarkup()
  }
}
