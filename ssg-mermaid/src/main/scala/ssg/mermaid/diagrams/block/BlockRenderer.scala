/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/block/blockRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from BlockDb + config -> SVG string
 *   Renames: blockRenderer draw() -> BlockRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package block

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders a block diagram to SVG. */
object BlockRenderer {

  private val Padding:    Double = 20.0
  private val CellWidth:  Double = 120.0
  private val CellHeight: Double = 60.0
  private val CellGap:    Double = 10.0

  /** Renders a block diagram to an SVG string. */
  def render(db: BlockDb, config: MermaidConfig): String = {
    val cols     = math.max(1, db.columns)
    val rowCount = db.rows.size.max(1)

    val svgWidth  = cols * (CellWidth + CellGap) + Padding * 2
    val svgHeight = rowCount * (CellHeight + CellGap) + Padding * 2 + 20
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: set role + aria-roledescription and (when present) the a11y title/desc,
    // mirroring addA11yInfo in mermaidAPI.ts:521-529. The block detector id is "block" (see
    // diagrams/block/blockDetector.ts) — mermaid passes the registered diagram type to
    // setA11yDiagramInfo(svg, diagramType).
    Accessibility.applyTo(svg, "block", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = BlockStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css)

    // Arrow marker
    val marker = defs.append("marker")
    marker.attr("id", "block-arrowhead")
    marker.attr("viewBox", "0 0 10 10")
    marker.attr("refX", 10).attr("refY", 5)
    marker.attr("markerWidth", 6).attr("markerHeight", 6)
    marker.attr("orient", "auto")
    val arrowPath = marker.append("path")
    arrowPath.attr("d", "M 0 0 L 10 5 L 0 10 z")
    arrowPath.style("fill", themeVars.lineColor)

    val mainGroup = svg.append("g")

    // Track block positions for edges
    val blockPositions = mutable.Map.empty[String, (Double, Double)]

    for ((row, rowIdx) <- db.rows.zipWithIndex) {
      var colOffset = 0
      for (block <- row.blocks) {
        val x = Padding + colOffset * (CellWidth + CellGap)
        val y = Padding + rowIdx * (CellHeight + CellGap)
        val w = block.width * CellWidth + (block.width - 1) * CellGap
        val h = CellHeight

        val rect = mainGroup.append("rect")
        rect.attr("x", x).attr("y", y)
        rect.attr("width", w).attr("height", h)
        rect.attr("rx", 5).attr("ry", 5)
        rect.classed("blockBox", true)

        val label = mainGroup.append("text")
        label.attr("x", x + w / 2.0).attr("y", y + h / 2.0 + 5)
        label.attr("text-anchor", "middle")
        label.classed("blockLabel", true)
        label.text(block.label)

        blockPositions(block.id) = (x + w / 2.0, y + h / 2.0)
        colOffset += block.width
      }
    }

    // Draw edges
    for ((from, to, edgeLabel) <- db.edges)
      for {
        (fx, fy) <- blockPositions.get(from)
        (tx, ty) <- blockPositions.get(to)
      } {
        val line = mainGroup.append("line")
        line.attr("x1", fx).attr("y1", fy)
        line.attr("x2", tx).attr("y2", ty)
        line.attr("marker-end", "url(#block-arrowhead)")
        line.classed("blockEdge", true)

        if (edgeLabel.nonEmpty) {
          val lbl = mainGroup.append("text")
          lbl.attr("x", (fx + tx) / 2).attr("y", (fy + ty) / 2 - 5)
          lbl.attr("text-anchor", "middle")
          lbl.classed("blockEdgeLabel", true)
          lbl.text(edgeLabel)
        }
      }

    svg.build().toMarkup()
  }
}
