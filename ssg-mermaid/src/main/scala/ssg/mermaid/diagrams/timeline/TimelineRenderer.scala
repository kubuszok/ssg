/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/timeline/timelineRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from TimelineDb + config -> SVG string; simple vertical layout
 *   Renames: timelineRenderer draw() -> TimelineRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package timeline

import ssg.mermaid.MermaidConfig
import ssg.mermaid.Accessibility
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a timeline diagram to SVG.
  *
  * Uses a simple vertical layout with periods and their events displayed as horizontal groups.
  */
object TimelineRenderer {

  private val DiagramPadding: Double = 20.0
  private val PeriodHeight:   Double = 50.0
  private val EventBoxWidth:  Double = 150.0
  private val EventBoxHeight: Double = 40.0
  private val EventGap:       Double = 10.0
  private val PeriodGap:      Double = 30.0
  private val TimelineLineX:  Double = 150.0

  /** Renders a timeline diagram to an SVG string.
    *
    * @param db
    *   the populated timeline database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: TimelineDb, config: MermaidConfig): String = {
    // Calculate dimensions
    var totalHeight = DiagramPadding
    if (db.title.nonEmpty) totalHeight += 30

    for (period <- db.periods) {
      val eventsHeight = math.max(period.events.length, 1) * (EventBoxHeight + EventGap)
      totalHeight += math.max(PeriodHeight, eventsHeight) + PeriodGap
    }

    val svgWidth  = TimelineLineX + EventBoxWidth + DiagramPadding * 3 + 100
    val svgHeight = totalHeight + DiagramPadding

    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "timeline", db.accTitle, db.accDescription)

    // Add defs with styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = TimelineStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    // Main group
    val mainGroup = svg.append("g")

    var y = DiagramPadding

    // Title
    if (db.title.nonEmpty) {
      val titleText = mainGroup.append("text")
      titleText.attr("x", svgWidth / 2.0)
      titleText.attr("y", y + 15)
      titleText.attr("text-anchor", "middle")
      titleText.classed("timelineTitleText", true)
      titleText.text(db.title)
      y += 30
    }

    // Draw the vertical timeline line
    val topY         = y
    val timelineLine = mainGroup.append("line")
    timelineLine.attr("x1", TimelineLineX)
    timelineLine.attr("y1", topY)
    timelineLine.attr("x2", TimelineLineX)
    timelineLine.attr("y2", totalHeight)
    timelineLine.classed("timelineLine", true)
    timelineLine.style("stroke", "#333")
    timelineLine.style("stroke-width", "2")

    // Current section label tracking
    var currentSection = ""

    // Render periods
    for ((period, idx) <- db.periods.zipWithIndex) {
      // Section header
      if (period.section.nonEmpty && period.section != currentSection) {
        currentSection = period.section
        val sectionLabel = mainGroup.append("text")
        sectionLabel.attr("x", TimelineLineX / 2.0)
        sectionLabel.attr("y", y + 15)
        sectionLabel.attr("text-anchor", "middle")
        sectionLabel.attr("font-weight", "bold")
        sectionLabel.classed("timelineSection", true)
        sectionLabel.text(currentSection)
        y += 25
      }

      // Period dot on the timeline line
      val dot = mainGroup.append("circle")
      dot.attr("cx", TimelineLineX)
      dot.attr("cy", y + PeriodHeight / 2.0)
      dot.attr("r", 5)
      dot.classed("timelineDot", true)
      dot.style("fill", "#333")

      // Period title (left of the line)
      val periodLabel = mainGroup.append("text")
      periodLabel.attr("x", TimelineLineX - 15)
      periodLabel.attr("y", y + PeriodHeight / 2.0 + 4)
      periodLabel.attr("text-anchor", "end")
      periodLabel.classed("timelinePeriod", true)
      periodLabel.text(period.title)

      // Events (right of the line)
      var eventY = y
      for ((event, eventIdx) <- period.events.zipWithIndex) {
        val eventX = TimelineLineX + 20

        // Connection line from dot to event
        val connector = mainGroup.append("line")
        connector.attr("x1", TimelineLineX + 5)
        connector.attr("y1", y + PeriodHeight / 2.0)
        connector.attr("x2", eventX)
        connector.attr("y2", eventY + EventBoxHeight / 2.0)
        connector.style("stroke", "#999")
        connector.style("stroke-width", "1")

        // Event box
        val eventRect = mainGroup.append("rect")
        eventRect.attr("x", eventX)
        eventRect.attr("y", eventY)
        eventRect.attr("width", EventBoxWidth)
        eventRect.attr("height", EventBoxHeight)
        eventRect.attr("rx", 5)
        eventRect.attr("ry", 5)
        eventRect.classed("timelineEvent", true)

        // Cycle through fill types for visual variety
        val fillIdx = (idx + eventIdx) % 8
        eventRect.classed(s"event-fill-$fillIdx", true)

        // Event text
        val eventText = mainGroup.append("text")
        eventText.attr("x", eventX + EventBoxWidth / 2.0)
        eventText.attr("y", eventY + EventBoxHeight / 2.0 + 4)
        eventText.attr("text-anchor", "middle")
        eventText.classed("timelineEventText", true)
        eventText.text(event)

        eventY += EventBoxHeight + EventGap
      }

      val eventsHeight = math.max(period.events.length, 1) * (EventBoxHeight + EventGap)
      y += math.max(PeriodHeight, eventsHeight) + PeriodGap
    }

    svg.build().toMarkup()
  }
}
