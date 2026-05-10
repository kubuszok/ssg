/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/requirement/requirementRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from RequirementDb + config -> SVG string
 *   Renames: requirementRenderer draw() -> RequirementRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package requirement

import ssg.mermaid.MermaidConfig
import ssg.mermaid.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a requirement diagram to SVG. */
object RequirementRenderer {

  private val Padding:     Double = 20.0
  private val NodeWidth:   Double = 200.0
  private val NodeHeight:  Double = 70.0
  private val NodeSpacing: Double = 30.0

  /** Renders a requirement diagram to an SVG string. */
  def render(db: RequirementDb, config: MermaidConfig): String = {
    val totalNodes = db.requirements.size + db.elements.size
    val cols       = math.max(1, math.ceil(math.sqrt(totalNodes.toDouble)).toInt)
    val rows       = math.max(1, math.ceil(totalNodes.toDouble / cols).toInt)

    val svgWidth  = cols * (NodeWidth + NodeSpacing) + Padding * 2
    val svgHeight = rows * (NodeHeight + NodeSpacing) + Padding * 3

    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = RequirementStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css)

    // Add arrow marker
    val marker = defs.append("marker")
    marker.attr("id", "req-arrowhead")
    marker.attr("viewBox", "0 0 10 10")
    marker.attr("refX", 10).attr("refY", 5)
    marker.attr("markerWidth", 6).attr("markerHeight", 6)
    marker.attr("orient", "auto")
    val arrow = marker.append("path")
    arrow.attr("d", "M 0 0 L 10 5 L 0 10 z")
    arrow.style("fill", themeVars.lineColor)

    val mainGroup = svg.append("g")

    // Position tracking for nodes
    val nodePositions = scala.collection.mutable.Map.empty[String, (Double, Double)]
    var nodeIdx       = 0

    // Draw requirements
    for ((name, req) <- db.requirements) {
      val col = nodeIdx % cols
      val row = nodeIdx / cols
      val x   = Padding + col * (NodeWidth + NodeSpacing)
      val y   = Padding + row * (NodeHeight + NodeSpacing)
      nodePositions(name) = (x + NodeWidth / 2, y + NodeHeight / 2)

      val nodeGroup = mainGroup.append("g")
      nodeGroup.attr("transform", s"translate($x, $y)")

      val rect = nodeGroup.append("rect")
      rect.attr("width", NodeWidth).attr("height", NodeHeight)
      rect.attr("rx", 5).attr("ry", 5)
      rect.classed("reqBox", true)

      // Type header
      val typeLabel = nodeGroup.append("text")
      typeLabel.attr("x", NodeWidth / 2).attr("y", 15)
      typeLabel.attr("text-anchor", "middle")
      typeLabel.classed("reqTypeLabel", true)
      typeLabel.text(s"<<${req.reqType}>>")

      // Name
      val nameLabel = nodeGroup.append("text")
      nameLabel.attr("x", NodeWidth / 2).attr("y", 35)
      nameLabel.attr("text-anchor", "middle")
      nameLabel.classed("reqNameLabel", true)
      nameLabel.text(req.name)

      // ID or risk/verify
      val infoText = {
        val parts = scala.collection.mutable.ArrayBuffer.empty[String]
        if (req.id.nonEmpty && req.id != name) parts += s"id: ${req.id}"
        if (req.risk.nonEmpty) parts += s"risk: ${req.risk}"
        if (req.verifyMethod.nonEmpty) parts += s"verify: ${req.verifyMethod}"
        parts.mkString(", ")
      }
      if (infoText.nonEmpty) {
        val info = nodeGroup.append("text")
        info.attr("x", NodeWidth / 2).attr("y", 55)
        info.attr("text-anchor", "middle")
        info.classed("reqInfoLabel", true)
        info.text(infoText)
      }

      nodeIdx += 1
    }

    // Draw elements
    for ((name, elem) <- db.elements) {
      val col = nodeIdx % cols
      val row = nodeIdx / cols
      val x   = Padding + col * (NodeWidth + NodeSpacing)
      val y   = Padding + row * (NodeHeight + NodeSpacing)
      nodePositions(name) = (x + NodeWidth / 2, y + NodeHeight / 2)

      val nodeGroup = mainGroup.append("g")
      nodeGroup.attr("transform", s"translate($x, $y)")

      val rect = nodeGroup.append("rect")
      rect.attr("width", NodeWidth).attr("height", NodeHeight)
      rect.attr("rx", 5).attr("ry", 5)
      rect.classed("elemBox", true)

      val typeLabel = nodeGroup.append("text")
      typeLabel.attr("x", NodeWidth / 2).attr("y", 15)
      typeLabel.attr("text-anchor", "middle")
      typeLabel.classed("reqTypeLabel", true)
      typeLabel.text("<<element>>")

      val nameLabel = nodeGroup.append("text")
      nameLabel.attr("x", NodeWidth / 2).attr("y", 35)
      nameLabel.attr("text-anchor", "middle")
      nameLabel.classed("reqNameLabel", true)
      nameLabel.text(elem.name)

      if (elem.docRef.nonEmpty) {
        val docLabel = nodeGroup.append("text")
        docLabel.attr("x", NodeWidth / 2).attr("y", 55)
        docLabel.attr("text-anchor", "middle")
        docLabel.classed("reqInfoLabel", true)
        docLabel.text(s"docRef: ${elem.docRef}")
      }

      nodeIdx += 1
    }

    // Draw relationships
    for (rel <- db.relationships)
      for {
        (sx, sy) <- nodePositions.get(rel.src)
        (dx, dy) <- nodePositions.get(rel.dst)
      } {
        val line = mainGroup.append("line")
        line.attr("x1", sx).attr("y1", sy)
        line.attr("x2", dx).attr("y2", dy)
        line.attr("marker-end", "url(#req-arrowhead)")
        line.classed("reqRelLine", true)

        // Relationship label at midpoint
        val label = mainGroup.append("text")
        label.attr("x", (sx + dx) / 2).attr("y", (sy + dy) / 2 - 5)
        label.attr("text-anchor", "middle")
        label.classed("reqRelLabel", true)
        label.text(rel.relType)
      }

    svg.build().toMarkup()
  }
}
