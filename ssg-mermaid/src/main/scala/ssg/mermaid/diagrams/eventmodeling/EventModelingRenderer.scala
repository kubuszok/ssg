/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid event modeling diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package eventmodeling

import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders an event modeling diagram to SVG. */
object EventModelingRenderer {

  private val LaneHeight:     Double              = 80.0
  private val EventWidth:     Double              = 120.0
  private val EventHeight:    Double              = 40.0
  private val EventSpacing:   Double              = 20.0
  private val Padding:        Double              = 30.0
  private val LaneLabelWidth: Double              = 100.0
  private val EventColors:    Map[String, String] = Map(
    "event" -> "#FFD700",
    "command" -> "#87CEEB",
    "view" -> "#98FB98"
  )

  def render(db: EventModelingDb, config: MermaidConfig): String = {
    val laneCount        = db.lanes.size.max(1)
    val maxEventsPerLane =
      if (db.lanes.isEmpty) 1
      else db.lanes.map(l => db.events.count(_.lane == l)).max.max(1)

    val svgWidth  = LaneLabelWidth + maxEventsPerLane * (EventWidth + EventSpacing) + Padding * 2
    val svgHeight = laneCount * LaneHeight + Padding * 3
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = EventModelingStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    val marker = defs.append("marker")
    marker.attr("id", "em-arrow").attr("viewBox", "0 0 10 10")
    marker.attr("refX", 10).attr("refY", 5).attr("markerWidth", 6).attr("markerHeight", 6).attr("orient", "auto")
    marker.append("path").attr("d", "M 0 0 L 10 5 L 0 10 z").style("fill", themeVars.lineColor)

    val mainGroup = svg.append("g")
    val positions = mutable.Map.empty[String, (Double, Double)]

    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", svgWidth / 2).attr("y", 25).attr("text-anchor", "middle").classed("emTitle", true).text(db.title)
    }

    for ((lane, laneIdx) <- db.lanes.zipWithIndex) {
      val y = Padding + 20 + laneIdx * LaneHeight
      // Lane background
      mainGroup
        .append("rect")
        .attr("x", Padding)
        .attr("y", y)
        .attr("width", svgWidth - Padding * 2)
        .attr("height", LaneHeight - 5)
        .style("fill", if (laneIdx % 2 == 0) "#fafafa" else "#f0f0f0")
        .style("stroke", "#ddd")
      // Lane label
      mainGroup.append("text").attr("x", Padding + 10).attr("y", y + LaneHeight / 2 + 5).classed("emLaneLabel", true).text(lane)

      val laneEvents = db.events.filter(_.lane == lane)
      for ((event, evIdx) <- laneEvents.zipWithIndex) {
        val ex    = LaneLabelWidth + Padding + evIdx * (EventWidth + EventSpacing)
        val ey    = y + (LaneHeight - EventHeight) / 2
        val color = EventColors.getOrElse(event.eventType, "#FFD700")
        positions(event.id) = (ex + EventWidth / 2, ey + EventHeight / 2)

        mainGroup.append("rect").attr("x", ex).attr("y", ey).attr("width", EventWidth).attr("height", EventHeight).attr("rx", 3).style("fill", color).style("stroke", "#999").classed("emEvent", true)
        mainGroup.append("text").attr("x", ex + EventWidth / 2).attr("y", ey + EventHeight / 2 + 5).attr("text-anchor", "middle").classed("emEventLabel", true).text(event.label)
      }
    }

    for ((fromId, toId) <- db.flows)
      for {
        (sx, sy) <- positions.get(fromId)
        (tx, ty) <- positions.get(toId)
      }
        mainGroup.append("line").attr("x1", sx).attr("y1", sy).attr("x2", tx).attr("y2", ty).attr("marker-end", "url(#em-arrow)").classed("emFlow", true)

    svg.build().toMarkup()
  }
}
