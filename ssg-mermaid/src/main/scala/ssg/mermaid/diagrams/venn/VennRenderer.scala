/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid venn diagram concept
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package venn

import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a Venn diagram to SVG. */
object VennRenderer {

  private val Radius:  Double        = 120.0
  private val Padding: Double        = 40.0
  private val Colors:  Array[String] = Array(
    "#4e79a7",
    "#f28e2b",
    "#e15759",
    "#76b7b2",
    "#59a14f"
  )

  def render(db: VennDb, config: MermaidConfig): String = {
    val setCount = db.sets.size.max(1)
    val size     = (Radius * 2 + Padding) * 2 + 40
    val cx       = size / 2; val cy = size / 2 + 20

    val viewBox = s"0 0 $size $size"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = VennStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    val mainGroup = svg.append("g")

    // Title
    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", cx).attr("y", 25).attr("text-anchor", "middle").classed("vennTitle", true).text(db.title)
    }

    // Position sets in a circle arrangement
    val angleStep = 2 * math.Pi / setCount
    val offset    = if (setCount <= 1) 0.0 else Radius * 0.6

    for ((vset, idx) <- db.sets.zipWithIndex) {
      val angle = angleStep * idx - math.Pi / 2
      val setX  = cx + offset * math.cos(angle)
      val setY  = cy + offset * math.sin(angle)
      val color = Colors(idx % Colors.length)

      val circle = mainGroup.append("circle")
      circle.attr("cx", setX).attr("cy", setY).attr("r", Radius)
      circle.style("fill", color).style("fill-opacity", "0.3")
      circle.style("stroke", color).style("stroke-width", "2")
      circle.classed("vennSet", true)

      // Label at the outer edge
      val labelX = cx + (offset + Radius * 0.6) * math.cos(angle)
      val labelY = cy + (offset + Radius * 0.6) * math.sin(angle)
      mainGroup.append("text").attr("x", labelX).attr("y", labelY + 5).attr("text-anchor", "middle").classed("vennSetLabel", true).text(vset.label)
    }

    // Intersection labels (placed at center for simplicity)
    for (isect <- db.intersections)
      if (isect.label.nonEmpty) {
        mainGroup.append("text").attr("x", cx).attr("y", cy + 5).attr("text-anchor", "middle").classed("vennIntersectionLabel", true).text(isect.label)
      }

    svg.build().toMarkup()
  }
}
