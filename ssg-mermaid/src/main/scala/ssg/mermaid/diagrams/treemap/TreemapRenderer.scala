/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid treemap diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package treemap

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a treemap diagram to SVG using a simple slice-and-dice layout. */
object TreemapRenderer {

  private val Padding:     Double        = 20.0
  private val ChartWidth:  Double        = 600.0
  private val ChartHeight: Double        = 400.0
  private val Colors:      Array[String] = Array(
    "#4e79a7",
    "#f28e2b",
    "#e15759",
    "#76b7b2",
    "#59a14f",
    "#edc948",
    "#b07aa1",
    "#ff9da7"
  )

  def render(db: TreemapDb, config: MermaidConfig): String = {
    val svgWidth  = ChartWidth + Padding * 2
    val svgHeight = ChartHeight + Padding * 2 + (if (db.title.nonEmpty) 40 else 0)
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "treemap", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = TreemapStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    val mainGroup = svg.append("g")
    var yOffset   = Padding
    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", svgWidth / 2).attr("y", 25).attr("text-anchor", "middle").classed("treemapTitle", true).text(db.title)
      yOffset += 30
    }

    // Flatten all leaf nodes for layout
    val leaves     = db.roots.flatMap(flattenLeaves)
    val totalValue = leaves.foldLeft(0.0)(_ + _.totalValue)

    if (totalValue > 0 && leaves.nonEmpty) {
      var xOffset = Padding
      for ((leaf, idx) <- leaves.zipWithIndex) {
        val fraction = leaf.totalValue / totalValue
        val w        = fraction * ChartWidth
        val color    = Colors(idx % Colors.length)

        mainGroup
          .append("rect")
          .attr("x", xOffset)
          .attr("y", yOffset)
          .attr("width", w)
          .attr("height", ChartHeight)
          .style("fill", color)
          .style("stroke", "white")
          .style("stroke-width", "2")
          .classed("treemapCell", true)

        // Label (only if cell is wide enough)
        if (w > 30) {
          mainGroup.append("text").attr("x", xOffset + w / 2).attr("y", yOffset + ChartHeight / 2 + 5).attr("text-anchor", "middle").classed("treemapLabel", true).text(leaf.label)
        }
        xOffset += w
      }
    }

    svg.build().toMarkup()
  }

  private def flattenLeaves(node: TreemapNode): Seq[TreemapNode] =
    if (node.children.isEmpty) Seq(node)
    else node.children.flatMap(flattenLeaves).toSeq
}
