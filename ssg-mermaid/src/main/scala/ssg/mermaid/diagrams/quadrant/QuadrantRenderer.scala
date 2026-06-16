/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/quadrant-chart/quadrantRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from QuadrantDb + config -> SVG string
 *   Renames: quadrantRenderer draw() -> QuadrantRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package quadrant

import ssg.mermaid.MermaidConfig
import ssg.mermaid.Accessibility
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a quadrant chart diagram to SVG. */
object QuadrantRenderer {

  private val Padding:   Double = 40.0
  private val ChartSize: Double = 500.0

  private val QuadrantColors: Array[String] = Array(
    "#f0f0ff",
    "#fff0f0",
    "#f0fff0",
    "#fffff0"
  )

  /** Renders a quadrant chart to an SVG string. */
  def render(db: QuadrantDb, config: MermaidConfig): String = {
    val svgWidth  = ChartSize + Padding * 3
    val svgHeight = ChartSize + Padding * 4
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "quadrantChart", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = QuadrantStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css)

    val mainGroup = svg.append("g")

    // Title
    var titleOffset = 0.0
    if (db.title.nonEmpty) {
      val titleText = mainGroup.append("text")
      titleText.attr("x", svgWidth / 2.0)
      titleText.attr("y", 25)
      titleText.attr("text-anchor", "middle")
      titleText.classed("quadrantTitleText", true)
      titleText.text(db.title)
      titleOffset = 30.0
    }

    val chartX     = Padding * 2
    val chartY     = Padding + titleOffset
    val chartGroup = mainGroup.append("g")
    chartGroup.attr("transform", s"translate($chartX, $chartY)")

    val halfSize = ChartSize / 2.0

    // Draw four quadrant backgrounds
    // Quadrant 1 = top-left, 2 = top-right, 3 = bottom-left, 4 = bottom-right
    val quadrantPositions = Array(
      (0.0, 0.0), // Q1: top-left
      (halfSize, 0.0), // Q2: top-right
      (0.0, halfSize), // Q3: bottom-left
      (halfSize, halfSize) // Q4: bottom-right
    )
    for (i <- 0 until 4) {
      val (qx, qy) = quadrantPositions(i)
      val rect     = chartGroup.append("rect")
      rect.attr("x", qx).attr("y", qy)
      rect.attr("width", halfSize).attr("height", halfSize)
      rect.style("fill", QuadrantColors(i))
      rect.style("stroke", "#ccc")
      rect.classed("quadrantArea", true)

      // Quadrant label
      if (db.quadrantLabels(i).nonEmpty) {
        val label = chartGroup.append("text")
        label.attr("x", qx + halfSize / 2.0)
        label.attr("y", qy + halfSize / 2.0)
        label.attr("text-anchor", "middle")
        label.attr("dominant-baseline", "central")
        label.classed("quadrantLabel", true)
        label.text(db.quadrantLabels(i))
      }
    }

    // Draw axes
    val xAxisLine = chartGroup.append("line")
    xAxisLine.attr("x1", 0).attr("y1", ChartSize).attr("x2", ChartSize).attr("y2", ChartSize)
    xAxisLine.classed("quadrantAxis", true)

    val yAxisLine = chartGroup.append("line")
    yAxisLine.attr("x1", 0).attr("y1", 0).attr("x2", 0).attr("y2", ChartSize)
    yAxisLine.classed("quadrantAxis", true)

    // Midlines
    val midX = chartGroup.append("line")
    midX.attr("x1", halfSize).attr("y1", 0).attr("x2", halfSize).attr("y2", ChartSize)
    midX.style("stroke", "#999").style("stroke-dasharray", "5,5")

    val midY = chartGroup.append("line")
    midY.attr("x1", 0).attr("y1", halfSize).attr("x2", ChartSize).attr("y2", halfSize)
    midY.style("stroke", "#999").style("stroke-dasharray", "5,5")

    // Axis labels
    if (db.xAxisLeftLabel.nonEmpty) {
      val label = mainGroup.append("text")
      label.attr("x", chartX).attr("y", chartY + ChartSize + 30)
      label.attr("text-anchor", "start")
      label.classed("quadrantAxisLabel", true)
      label.text(db.xAxisLeftLabel)
    }
    if (db.xAxisRightLabel.nonEmpty) {
      val label = mainGroup.append("text")
      label.attr("x", chartX + ChartSize).attr("y", chartY + ChartSize + 30)
      label.attr("text-anchor", "end")
      label.classed("quadrantAxisLabel", true)
      label.text(db.xAxisRightLabel)
    }
    if (db.yAxisBottomLabel.nonEmpty) {
      val label = mainGroup.append("text")
      label.attr("x", chartX - 10).attr("y", chartY + ChartSize)
      label.attr("text-anchor", "end")
      label.classed("quadrantAxisLabel", true)
      label.text(db.yAxisBottomLabel)
    }
    if (db.yAxisTopLabel.nonEmpty) {
      val label = mainGroup.append("text")
      label.attr("x", chartX - 10).attr("y", chartY + 10)
      label.attr("text-anchor", "end")
      label.classed("quadrantAxisLabel", true)
      label.text(db.yAxisTopLabel)
    }

    // Draw data points
    for (point <- db.points) {
      val px = point.x * ChartSize
      val py = (1.0 - point.y) * ChartSize // invert Y (0 at bottom)

      val circle = chartGroup.append("circle")
      circle.attr("cx", px).attr("cy", py).attr("r", 6)
      circle.classed("quadrantPoint", true)

      val label = chartGroup.append("text")
      label.attr("x", px + 10).attr("y", py + 4)
      label.attr("text-anchor", "start")
      label.classed("quadrantPointLabel", true)
      label.text(point.label)
    }

    svg.build().toMarkup()
  }
}
