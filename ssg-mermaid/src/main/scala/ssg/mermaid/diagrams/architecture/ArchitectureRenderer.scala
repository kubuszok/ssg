/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the architecture diagram renderer.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package architecture

import ssg.mermaid.MermaidConfig
import ssg.mermaid.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders an architecture diagram to SVG. */
object ArchitectureRenderer {

  private val Padding:      Double = 30.0
  private val NodeWidth:    Double = 120.0
  private val NodeHeight:   Double = 60.0
  private val NodeSpacing:  Double = 40.0
  private val JunctionSize: Double = 12.0

  def render(db: ArchitectureDb, config: MermaidConfig): String = {
    val totalNodes = db.nodes.size
    val cols       = math.max(1, math.ceil(math.sqrt(totalNodes.toDouble)).toInt)
    val rows       = math.max(1, math.ceil(totalNodes.toDouble / cols).toInt)

    val svgWidth  = cols * (NodeWidth + NodeSpacing) + Padding * 2
    val svgHeight = rows * (NodeHeight + NodeSpacing) + Padding * 2
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = ArchitectureStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    defs.append("style").attr("type", "text/css").text(baseCss + "\n" + css)

    val marker = defs.append("marker")
    marker.attr("id", "arch-arrowhead").attr("viewBox", "0 0 10 10")
    marker.attr("refX", 10).attr("refY", 5).attr("markerWidth", 6).attr("markerHeight", 6).attr("orient", "auto")
    marker.append("path").attr("d", "M 0 0 L 10 5 L 0 10 z").style("fill", themeVars.lineColor)

    val mainGroup = svg.append("g")
    val positions = mutable.Map.empty[String, (Double, Double)]
    var idx       = 0

    for (node <- db.nodes) {
      val col = idx % cols; val row = idx / cols
      val x   = Padding + col * (NodeWidth + NodeSpacing)
      val y   = Padding + row * (NodeHeight + NodeSpacing)
      positions(node.id) = (x + NodeWidth / 2, y + NodeHeight / 2)

      if (node.nodeType == "junction") {
        val c = mainGroup.append("circle")
        c.attr("cx", x + NodeWidth / 2).attr("cy", y + NodeHeight / 2).attr("r", JunctionSize)
        c.classed("archJunction", true)
      } else {
        val g = mainGroup.append("g").attr("transform", s"translate($x, $y)")
        g.append("rect").attr("width", NodeWidth).attr("height", NodeHeight).attr("rx", 8).attr("ry", 8).classed("archService", true)
        g.append("text").attr("x", NodeWidth / 2).attr("y", NodeHeight / 2 + 5).attr("text-anchor", "middle").classed("archLabel", true).text(node.label)
      }
      idx += 1
    }

    for (edge <- db.edges)
      for {
        (sx, sy) <- positions.get(edge.fromId)
        (tx, ty) <- positions.get(edge.toId)
      } {
        val line = mainGroup.append("line")
        line.attr("x1", sx).attr("y1", sy).attr("x2", tx).attr("y2", ty)
        line.attr("marker-end", "url(#arch-arrowhead)").classed("archEdge", true)
        if (edge.label.nonEmpty) {
          mainGroup.append("text").attr("x", (sx + tx) / 2).attr("y", (sy + ty) / 2 - 5).attr("text-anchor", "middle").classed("archEdgeLabel", true).text(edge.label)
        }
      }

    svg.build().toMarkup()
  }
}
