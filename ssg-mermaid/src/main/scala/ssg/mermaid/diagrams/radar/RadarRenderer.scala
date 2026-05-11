/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the radar chart renderer.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package radar

import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a radar chart to SVG. */
object RadarRenderer {

  private val Radius:  Double        = 180.0
  private val Padding: Double        = 60.0
  private val Levels:  Int           = 5
  private val Colors:  Array[String] = Array(
    "#4e79a7",
    "#f28e2b",
    "#e15759",
    "#76b7b2",
    "#59a14f"
  )

  def render(db: RadarDb, config: MermaidConfig): String = {
    val size    = (Radius + Padding) * 2
    val viewBox = s"0 0 $size $size"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = RadarStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    val mainGroup = svg.append("g")
    val axisCount = db.axes.size
    if (axisCount > 0) {
      renderContent(mainGroup, db, themeVars, axisCount)
    }

    svg.build().toMarkup()
  }

  private def renderContent(
    mainGroup: SvgBuilder,
    db:        RadarDb,
    themeVars: ssg.mermaid.theme.ThemeVariables,
    axisCount: Int
  ): Unit = {
    val cx        = Radius + Padding; val cy = Radius + Padding
    val angleStep = 2 * math.Pi / axisCount

    // Draw grid rings
    for (level <- 1 to Levels) {
      val r       = Radius * level / Levels
      val polygon = mainGroup.append("polygon")
      val points  = (0 until axisCount).map { i =>
        val angle = angleStep * i - math.Pi / 2
        val x     = cx + r * math.cos(angle)
        val y     = cy + r * math.sin(angle)
        s"$x,$y"
      }
      polygon.attr("points", points.mkString(" "))
      polygon.classed("radarGrid", true)
    }

    // Draw axis lines and labels
    for ((axis, i) <- db.axes.zipWithIndex) {
      val angle = angleStep * i - math.Pi / 2
      val x     = cx + Radius * math.cos(angle)
      val y     = cy + Radius * math.sin(angle)

      val line = mainGroup.append("line")
      line.attr("x1", cx).attr("y1", cy).attr("x2", x).attr("y2", y)
      line.classed("radarAxis", true)

      val labelX = cx + (Radius + 20) * math.cos(angle)
      val labelY = cy + (Radius + 20) * math.sin(angle)
      mainGroup.append("text").attr("x", labelX).attr("y", labelY + 4).attr("text-anchor", "middle").classed("radarAxisLabel", true).text(axis)
    }

    // Draw data series
    for ((series, sIdx) <- db.series.zipWithIndex) {
      val color = Colors(sIdx % Colors.length)
      if (series.values.nonEmpty) {
        val points = (0 until axisCount).map { i =>
          val value        = if (i < series.values.size) series.values(i) else 0.0
          val clampedValue = math.min(1.0, math.max(0.0, value))
          val angle        = angleStep * i - math.Pi / 2
          val x            = cx + Radius * clampedValue * math.cos(angle)
          val y            = cy + Radius * clampedValue * math.sin(angle)
          s"$x,$y"
        }
        val polygon = mainGroup.append("polygon")
        polygon.attr("points", points.mkString(" "))
        polygon.style("fill", color).style("fill-opacity", "0.3")
        polygon.style("stroke", color).style("stroke-width", "2")
        polygon.classed("radarSeries", true)

        // Data point dots
        for (i <- 0 until axisCount) {
          val value        = if (i < series.values.size) series.values(i) else 0.0
          val clampedValue = math.min(1.0, math.max(0.0, value))
          val angle        = angleStep * i - math.Pi / 2
          val x            = cx + Radius * clampedValue * math.cos(angle)
          val y            = cy + Radius * clampedValue * math.sin(angle)
          mainGroup.append("circle").attr("cx", x).attr("cy", y).attr("r", 4).style("fill", color).classed("radarPoint", true)
        }
      }
    }

    // Title
    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", cx).attr("y", 20).attr("text-anchor", "middle").classed("radarTitle", true).text(db.title)
    }
  }
}
