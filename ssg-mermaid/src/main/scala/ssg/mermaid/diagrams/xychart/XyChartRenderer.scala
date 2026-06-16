/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/xychart/xychartRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from XyChartDb + config -> SVG string
 *   Renames: xychartRenderer draw() -> XyChartRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package xychart

import ssg.mermaid.MermaidConfig
import ssg.mermaid.Accessibility
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders an XY chart diagram to SVG. */
object XyChartRenderer {

  private val Padding:     Double        = 40.0
  private val ChartWidth:  Double        = 600.0
  private val ChartHeight: Double        = 400.0
  private val BarColors:   Array[String] = Array(
    "#4e79a7",
    "#f28e2b",
    "#e15759",
    "#76b7b2",
    "#59a14f",
    "#edc948",
    "#b07aa1",
    "#ff9da7"
  )

  /** Renders an XY chart to an SVG string. */
  def render(db: XyChartDb, config: MermaidConfig): String = {
    val svgWidth  = ChartWidth + Padding * 3
    val svgHeight = ChartHeight + Padding * 4
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "xychart", db.accTitle, db.accDescription)

    // Styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = XyChartStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css)

    val mainGroup = svg.append("g")

    // Title
    var titleOffset = 0.0
    if (db.title.nonEmpty) {
      val titleText = mainGroup.append("text")
      titleText.attr("x", svgWidth / 2.0)
      titleText.attr("y", 25)
      titleText.attr("text-anchor", "middle")
      titleText.classed("xychartTitleText", true)
      titleText.text(db.title)
      titleOffset = 30.0
    }

    val chartX     = Padding * 2
    val chartY     = Padding + titleOffset
    val chartGroup = mainGroup.append("g")
    chartGroup.attr("transform", s"translate($chartX, $chartY)")

    if (db.dataSeries.nonEmpty) {
      // Compute Y axis range
      val maxVal = {
        val m = db.maxValue
        if (!db.yAxisMax.isNaN) db.yAxisMax else if (m > 0) m * 1.1 else 10.0
      }
      val minVal = if (!db.yAxisMin.isNaN) db.yAxisMin else 0.0
      val yRange = maxVal - minVal

      // Draw axes
      drawAxes(chartGroup, db, ChartWidth, ChartHeight, minVal, maxVal)

      // Draw data series
      val dataCount = db.dataPointCount
      if (dataCount > 0) {
        var seriesIdx = 0
        for (series <- db.dataSeries) {
          val color = BarColors(seriesIdx % BarColors.length)
          series.seriesType match {
            case "bar" =>
              drawBars(
                chartGroup,
                series,
                dataCount,
                db.dataSeries.count(_.seriesType == "bar"),
                seriesIdx,
                ChartWidth,
                ChartHeight,
                minVal,
                yRange,
                color
              )
            case "line" =>
              drawLine(chartGroup, series, dataCount, ChartWidth, ChartHeight, minVal, yRange, color)
            case _ => // ignore
          }
          seriesIdx += 1
        }
      }

      // Axis labels
      if (db.xAxisLabel.nonEmpty) {
        val label = mainGroup.append("text")
        label.attr("x", chartX + ChartWidth / 2.0)
        label.attr("y", chartY + ChartHeight + 50)
        label.attr("text-anchor", "middle")
        label.classed("xychartAxisLabel", true)
        label.text(db.xAxisLabel)
      }
      if (db.yAxisLabel.nonEmpty) {
        val label = mainGroup.append("text")
        label.attr("x", 15)
        label.attr("y", chartY + ChartHeight / 2.0)
        label.attr("text-anchor", "middle")
        label.attr("transform", s"rotate(-90, 15, ${chartY + ChartHeight / 2.0})")
        label.classed("xychartAxisLabel", true)
        label.text(db.yAxisLabel)
      }
    }

    svg.build().toMarkup()
  }

  private def drawAxes(group: SvgBuilder, db: XyChartDb, w: Double, h: Double, minVal: Double, maxVal: Double): Unit = {
    // X axis line
    val xAxis = group.append("line")
    xAxis.attr("x1", 0).attr("y1", h).attr("x2", w).attr("y2", h)
    xAxis.classed("xychartAxis", true)

    // Y axis line
    val yAxis = group.append("line")
    yAxis.attr("x1", 0).attr("y1", 0).attr("x2", 0).attr("y2", h)
    yAxis.classed("xychartAxis", true)

    // Y axis ticks (5 ticks)
    val yRange = maxVal - minVal
    for (i <- 0 to 4) {
      val frac  = i.toDouble / 4.0
      val yPos  = h - frac * h
      val value = minVal + frac * yRange

      val tick = group.append("line")
      tick.attr("x1", -5).attr("y1", yPos).attr("x2", 0).attr("y2", yPos)
      tick.classed("xychartTick", true)

      val label = group.append("text")
      label.attr("x", -10).attr("y", yPos + 4)
      label.attr("text-anchor", "end")
      label.classed("xychartTickLabel", true)
      // Round half-up to Latin digits (ISS-1156): `f"$value%.0f"` followed
      // Locale.getDefault, which under CLDR providers can vary the digit set.
      label.text(Math.round(value).toString)
    }

    // X axis category labels
    if (db.xAxisCategories.nonEmpty) {
      val step = w / db.xAxisCategories.size
      for ((cat, idx) <- db.xAxisCategories.zipWithIndex) {
        val xPos  = step * idx + step / 2.0
        val label = group.append("text")
        label.attr("x", xPos).attr("y", h + 20)
        label.attr("text-anchor", "middle")
        label.classed("xychartTickLabel", true)
        label.text(cat)
      }
    }
  }

  private def drawBars(group: SvgBuilder, series: DataSeries, dataCount: Int, barSeriesCount: Int, seriesIdx: Int, w: Double, h: Double, minVal: Double, yRange: Double, color: String): Unit =
    if (dataCount != 0 && yRange != 0) {
      val groupWidth = w / dataCount
      val barWidth   = groupWidth / (barSeriesCount + 1)
      val barOffset  = barWidth * seriesIdx

      for ((value, idx) <- series.data.zipWithIndex) {
        val barHeight = ((value - minVal) / yRange) * h
        val x         = groupWidth * idx + barOffset + barWidth * 0.1
        val y         = h - barHeight
        val rect      = group.append("rect")
        rect.attr("x", x).attr("y", y)
        rect.attr("width", barWidth * 0.8).attr("height", barHeight)
        rect.style("fill", color)
        rect.classed("xychartBar", true)
      }
    }

  private def drawLine(group: SvgBuilder, series: DataSeries, dataCount: Int, w: Double, h: Double, minVal: Double, yRange: Double, color: String): Unit =
    if (dataCount != 0 && yRange != 0 && series.data.nonEmpty) {
      val step   = w / dataCount
      val points = series.data.zipWithIndex.map { case (value, idx) =>
        val x = step * idx + step / 2.0
        val y = h - ((value - minVal) / yRange) * h
        s"$x,$y"
      }

      val polyline = group.append("polyline")
      polyline.attr("points", points.mkString(" "))
      polyline.style("fill", "none")
      polyline.style("stroke", color)
      polyline.style("stroke-width", "2")
      polyline.classed("xychartLine", true)

      // Data point circles
      for ((value, idx) <- series.data.zipWithIndex) {
        val x      = step * idx + step / 2.0
        val y      = h - ((value - minVal) / yRange) * h
        val circle = group.append("circle")
        circle.attr("cx", x).attr("cy", y).attr("r", 4)
        circle.style("fill", color)
        circle.classed("xychartLinePoint", true)
      }
    }
}
