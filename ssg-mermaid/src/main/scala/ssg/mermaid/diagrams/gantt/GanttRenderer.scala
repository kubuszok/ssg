/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/gantt/ganttRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from GanttDb + config -> SVG string; custom timeline layout
 *   Renames: ganttRenderer draw() -> GanttRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package gantt

import ssg.mermaid.MermaidConfig
import ssg.mermaid.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Renders a Gantt chart to SVG.
  *
  * Takes a populated [[GanttDb]] and produces a complete SVG string. Uses horizontal bars on a time axis.
  */
object GanttRenderer {

  private val DiagramPadding: Double = 20.0

  /** Renders a Gantt chart to an SVG string.
    *
    * @param db
    *   the populated Gantt database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: GanttDb, config: MermaidConfig): String = {
    val ganttConfig  = config.gantt
    val barHeight    = ganttConfig.barHeight.toDouble
    val barGap       = ganttConfig.barGap.toDouble
    val leftPadding  = ganttConfig.leftPadding.toDouble
    val topPadding   = ganttConfig.topPadding.toDouble
    val rightPadding = ganttConfig.rightPadding.toDouble

    if (db.tasks.isEmpty) {
      val svg = SvgBuilder.createSvg("0 0 400 100")
      svg.attr("role", "img")
      val text = svg.append("text")
      text.attr("x", 200)
      text.attr("y", 50)
      text.attr("text-anchor", "middle")
      text.text("No tasks defined")
      svg.build().toMarkup()
    } else {
      renderTasks(db, config, barHeight, barGap, leftPadding, topPadding, rightPadding)
    }
  }

  private def renderTasks(
    db:           GanttDb,
    config:       MermaidConfig,
    barHeight:    Double,
    barGap:       Double,
    leftPadding:  Double,
    topPadding:   Double,
    rightPadding: Double
  ): String = {
    val ganttConfig = config.gantt
    val minDate     = db.minDate
    val maxDate     = db.maxDate
    val totalDays   = math.max(ChronoUnit.DAYS.between(minDate, maxDate).toDouble, 1.0)

    // Chart area width (the timeline portion)
    val chartWidth = 600.0 // Fixed chart width for server-side rendering
    val dayWidth   = chartWidth / totalDays

    // Compute total height
    val totalTasks    = db.tasks.length
    val totalSections = db.sections.length
    val totalHeight   = topPadding + totalTasks * (barHeight + barGap) + totalSections * 20 + DiagramPadding

    // SVG dimensions
    val svgWidth  = leftPadding + chartWidth + rightPadding
    val svgHeight = totalHeight

    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Add defs with styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = GanttStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css)

    // Main group
    val mainGroup = svg.append("g")

    // Title
    var yOffset = 0.0
    if (db.title.nonEmpty) {
      val titleText = mainGroup.append("text")
      titleText.attr("x", svgWidth / 2.0)
      titleText.attr("y", ganttConfig.titleTopMargin)
      titleText.attr("text-anchor", "middle")
      titleText.classed("titleText", true)
      titleText.text(db.title)
      yOffset = ganttConfig.titleTopMargin + 10
    }

    // Time axis
    val axisGroup = mainGroup.append("g")
    axisGroup.classed("grid", true)
    renderTimeAxis(axisGroup, minDate, maxDate, leftPadding, yOffset + topPadding - 20, chartWidth, dayWidth, totalDays)

    // Section backgrounds and task bars
    var currentY   = yOffset + topPadding
    var sectionIdx = 0

    for (section <- db.sections) {
      // Section label
      if (section.name.nonEmpty) {
        val sectionBg = mainGroup.append("rect")
        sectionBg.attr("x", 0)
        sectionBg.attr("y", currentY - 5)
        sectionBg.attr("width", svgWidth)
        sectionBg.attr("height", section.tasks.length * (barHeight + barGap) + 20)
        sectionBg.classed(if (sectionIdx % 2 == 0) "section0" else "section1", true)

        val sectionLabel = mainGroup.append("text")
        sectionLabel.attr("x", 10)
        sectionLabel.attr("y", currentY + 10)
        sectionLabel.classed("sectionTitle", true)
        sectionLabel.text(section.name)

        currentY += 20
      }

      // Tasks
      for (task <- section.tasks) {
        val taskStartDays = ChronoUnit.DAYS.between(minDate, task.startDate).toDouble
        val taskDuration  = math.max(ChronoUnit.DAYS.between(task.startDate, task.endDate).toDouble, 0.5)

        val taskX     = leftPadding + taskStartDays * dayWidth
        val taskWidth = math.max(taskDuration * dayWidth, 2.0)

        // Task bar
        val bar = mainGroup.append("rect")
        bar.attr("x", taskX)
        bar.attr("y", currentY)
        bar.attr("width", taskWidth)
        bar.attr("height", barHeight)
        bar.attr("rx", 3)
        bar.attr("ry", 3)

        // Apply status-based CSS classes
        if (task.isDone) bar.classed("done", true)
        if (task.isCrit) bar.classed("crit", true)
        if (task.isActive) bar.classed("active", true)
        if (task.isMilestone) {
          bar.classed("milestone", true)
        }
        bar.classed("task", true)

        // Task label
        val label = mainGroup.append("text")
        label.attr("x", taskX + taskWidth / 2.0)
        label.attr("y", currentY + barHeight / 2.0 + 4)
        label.attr("text-anchor", "middle")
        label.classed("taskText", true)
        label.text(task.name)

        currentY += barHeight + barGap
      }

      sectionIdx += 1
    }

    // Handle tasks not in any section
    val orphanTasks = db.tasks.filter(t => t.section.isEmpty)
    for (task <- orphanTasks) {
      val taskStartDays = ChronoUnit.DAYS.between(minDate, task.startDate).toDouble
      val taskDuration  = math.max(ChronoUnit.DAYS.between(task.startDate, task.endDate).toDouble, 0.5)

      val taskX     = leftPadding + taskStartDays * dayWidth
      val taskWidth = math.max(taskDuration * dayWidth, 2.0)

      val bar = mainGroup.append("rect")
      bar.attr("x", taskX)
      bar.attr("y", currentY)
      bar.attr("width", taskWidth)
      bar.attr("height", barHeight)
      bar.attr("rx", 3)
      bar.attr("ry", 3)
      bar.classed("task", true)

      val label = mainGroup.append("text")
      label.attr("x", taskX + taskWidth / 2.0)
      label.attr("y", currentY + barHeight / 2.0 + 4)
      label.attr("text-anchor", "middle")
      label.classed("taskText", true)
      label.text(task.name)

      currentY += barHeight + barGap
    }

    svg.build().toMarkup()
  }

  /** Renders the time axis with date labels and grid lines. */
  private def renderTimeAxis(
    parent:      SvgBuilder,
    minDate:     LocalDate,
    maxDate:     LocalDate,
    leftPadding: Double,
    y:           Double,
    chartWidth:  Double,
    dayWidth:    Double,
    totalDays:   Double
  ): Unit = {
    // Determine tick interval based on total days
    val tickInterval =
      if (totalDays <= 14) 1
      else if (totalDays <= 60) 7
      else if (totalDays <= 365) 30
      else 90

    var current = minDate
    while (!current.isAfter(maxDate)) {
      val dayOffset = ChronoUnit.DAYS.between(minDate, current).toDouble
      val x         = leftPadding + dayOffset * dayWidth

      // Grid line
      val line = parent.append("line")
      line.attr("x1", x)
      line.attr("y1", y)
      line.attr("x2", x)
      line.attr("y2", y + 2000) // extend down to cover all tasks
      line.classed("grid", true)
      line.style("stroke", "#ccc")
      line.style("stroke-width", "0.5")

      // Date label
      val label = parent.append("text")
      label.attr("x", x)
      label.attr("y", y)
      label.attr("text-anchor", "middle")
      label.classed("axisLabel", true)
      label.attr("font-size", "10")
      label.text(current.toString)

      current = current.plusDays(tickInterval.toLong)
    }
  }
}
