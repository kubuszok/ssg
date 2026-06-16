/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/c4/c4Renderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package c4

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders a C4 diagram to SVG. */
object C4Renderer {

  private val Padding:      Double = 30.0
  private val NodeWidth:    Double = 180.0
  private val NodeHeight:   Double = 100.0
  private val NodeSpacing:  Double = 40.0
  private val PersonHeight: Double = 120.0

  def render(db: C4Db, config: MermaidConfig): String = {
    val totalNodes = db.entities.size
    val cols       = math.max(1, math.ceil(math.sqrt(totalNodes.toDouble)).toInt)
    val rows       = math.max(1, math.ceil(totalNodes.toDouble / cols).toInt)

    val svgWidth  = cols * (NodeWidth + NodeSpacing) + Padding * 2
    val svgHeight = rows * (PersonHeight + NodeSpacing) + Padding * 3
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "c4", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = C4Styles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    val marker = defs.append("marker")
    marker.attr("id", "c4-arrow").attr("viewBox", "0 0 10 10")
    marker.attr("refX", 10).attr("refY", 5).attr("markerWidth", 6).attr("markerHeight", 6).attr("orient", "auto")
    marker.append("path").attr("d", "M 0 0 L 10 5 L 0 10 z").style("fill", themeVars.lineColor)

    val mainGroup = svg.append("g")
    val positions = mutable.Map.empty[String, (Double, Double)]

    // Title
    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", svgWidth / 2).attr("y", 25).attr("text-anchor", "middle").classed("c4Title", true).text(db.title)
    }

    var idx = 0
    for (entity <- db.entities) {
      val col = idx % cols; val row       = idx / cols
      val x   = Padding + col * (NodeWidth + NodeSpacing)
      val y   = Padding + 20 + row * (PersonHeight + NodeSpacing)
      val cx  = x + NodeWidth / 2; val cy = y + NodeHeight / 2
      positions(entity.alias) = (cx, cy)

      val g = mainGroup.append("g").attr("transform", s"translate($x, $y)")

      if (entity.entityType == "person") {
        // Person shape: circle head + rectangle body
        g.append("circle").attr("cx", NodeWidth / 2).attr("cy", 20).attr("r", 20).classed("c4Person", true)
        g.append("rect").attr("x", 10).attr("y", 45).attr("width", NodeWidth - 20).attr("height", 50).attr("rx", 5).classed("c4PersonBody", true)
        g.append("text").attr("x", NodeWidth / 2).attr("y", 75).attr("text-anchor", "middle").classed("c4Label", true).text(entity.label)
      } else {
        val cssClass = entity.entityType match {
          case "system"    => "c4System"
          case "container" => "c4Container"
          case "component" => "c4Component"
          case _           => "c4System"
        }
        g.append("rect").attr("width", NodeWidth).attr("height", NodeHeight).attr("rx", 5).classed(cssClass, true)
        g.append("text").attr("x", NodeWidth / 2).attr("y", 20).attr("text-anchor", "middle").classed("c4TypeLabel", true).text(s"[${entity.entityType}]")
        g.append("text").attr("x", NodeWidth / 2).attr("y", 45).attr("text-anchor", "middle").classed("c4Label", true).text(entity.label)
        if (entity.description.nonEmpty) {
          g.append("text").attr("x", NodeWidth / 2).attr("y", 65).attr("text-anchor", "middle").classed("c4Desc", true).text(entity.description)
        }
        if (entity.technology.nonEmpty) {
          g.append("text").attr("x", NodeWidth / 2).attr("y", 85).attr("text-anchor", "middle").classed("c4Tech", true).text(s"[${entity.technology}]")
        }
      }
      idx += 1
    }

    // Draw relationships
    for (rel <- db.relationships)
      for {
        (sx, sy) <- positions.get(rel.from)
        (tx, ty) <- positions.get(rel.to)
      } {
        mainGroup.append("line").attr("x1", sx).attr("y1", sy).attr("x2", tx).attr("y2", ty).attr("marker-end", "url(#c4-arrow)").classed("c4Rel", true)

        if (rel.label.nonEmpty) {
          mainGroup.append("text").attr("x", (sx + tx) / 2).attr("y", (sy + ty) / 2 - 5).attr("text-anchor", "middle").classed("c4RelLabel", true).text(rel.label)
        }
      }

    svg.build().toMarkup()
  }
}
