/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/pie/pieRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from PieDb + config -> SVG string; arc paths computed mathematically
 *   Renames: pieRenderer draw() -> PieRenderer.render()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package pie

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a pie chart diagram to SVG.
  *
  * Takes a populated [[PieDb]] and produces a complete SVG string. The rendering pipeline:
  *   1. Compute arc angles from section values
  *   1. Create SVG root with viewBox
  *   1. Render pie slices as path elements with arc commands
  *   1. Render labels for each slice
  *   1. Render legend
  *   1. Add theme styles
  *   1. Return SVG string
  */
object PieRenderer {

  /** Diagram padding around the entire pie chart. */
  private val DiagramPadding: Double = 20.0

  /** Default pie chart radius. */
  private val DefaultRadius: Double = 140.0

  /** Legend box dimensions. */
  private val LegendRectSize: Int = 18
  private val LegendSpacing:  Int = 4

  /** Renders a pie chart to an SVG string.
    *
    * @param db
    *   the populated pie chart database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: PieDb, config: MermaidConfig): String = {
    val radius    = DefaultRadius
    val centerX   = radius + DiagramPadding + 100 // extra for legend
    val centerY   = radius + DiagramPadding + 40 // extra for title
    val svgWidth  = centerX + radius + DiagramPadding + 200 // space for legend
    val svgHeight = centerY + radius + DiagramPadding

    // Create SVG root
    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "pie", db.accTitle, db.accDescription)

    // Add defs with styles
    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = PieStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    styleEl.text(baseCss + "\n" + css)

    // Main group
    val mainGroup = svg.append("g")

    // Render title if present
    var titleOffset = 0.0
    if (db.title.nonEmpty) {
      val titleText = mainGroup.append("text")
      titleText.attr("x", centerX)
      titleText.attr("y", 25)
      titleText.attr("text-anchor", "middle")
      titleText.classed("pieTitleText", true)
      titleText.text(db.title)
      titleOffset = 20.0
    }

    // Compute arcs
    val totalValue = db.total
    if (totalValue > 0 && db.sections.nonEmpty) {
      // Pie group centered
      val pieGroup = mainGroup.append("g")
      pieGroup.attr("transform", s"translate($centerX, ${centerY + titleOffset})")

      // Draw slices
      var startAngle = 0.0
      for ((section, idx) <- db.sections.zipWithIndex) {
        val sliceAngle = (section.value / totalValue) * 2.0 * math.Pi
        val endAngle   = startAngle + sliceAngle

        val colorIdx  = idx % 13
        val fillColor =
          if (themeVars.pie(colorIdx).nonEmpty) themeVars.pie(colorIdx)
          else defaultPieColor(colorIdx)

        // Create arc path
        val path = createArcPath(0, 0, radius, startAngle, endAngle)

        val slice = pieGroup.append("path")
        slice.attr("d", path)
        slice.classed("pieCircle", true)
        slice.style("fill", fillColor)
        slice.style("opacity", themeVars.pieOpacity)
        slice.style("stroke", themeVars.pieStrokeColor)
        slice.style("stroke-width", themeVars.pieStrokeWidth)

        // Label at midpoint of arc
        val textPosition = config.pie.textPosition
        val midAngle     = startAngle + sliceAngle / 2.0
        val labelRadius  = radius * textPosition
        val labelX       = labelRadius * math.cos(midAngle - math.Pi / 2.0)
        val labelY       = labelRadius * math.sin(midAngle - math.Pi / 2.0)

        // Only show percentage label if slice is large enough
        val percentage = (section.value / totalValue) * 100.0
        if (percentage > 3.0) {
          val label = pieGroup.append("text")
          label.attr("x", labelX)
          label.attr("y", labelY)
          label.attr("text-anchor", "middle")
          label.attr("dominant-baseline", "central")
          label.classed("slice", true)
          // Locale-independent formatting (ISS-1156): the port renders one
          // decimal place for the percentage (upstream pieRenderer.ts:119 uses
          // toFixed(0)); toFixed keeps the single fraction digit (e.g. 50.0 ->
          // "50.0", matching JS toFixed(1)), and the integer value count rounds
          // half-up to Latin digits. The previous `f"...%.1f"`/`"%.0f".format`
          // followed Locale.getDefault and emitted "33,3%" on comma-locale JVMs.
          val percentStr = ssg.graphs.commons.util.FormatUtil.toFixed(percentage, 1)
          val labelText  = if (db.showData) {
            s"$percentStr% (${Math.round(section.value).toString})"
          } else {
            s"$percentStr%"
          }
          label.text(labelText)
        }

        startAngle = endAngle
      }

      // Render legend
      val legendGroup = mainGroup.append("g")
      legendGroup.attr("transform", s"translate(${centerX + radius + 40}, ${DiagramPadding + titleOffset})")

      for ((section, idx) <- db.sections.zipWithIndex) {
        val colorIdx  = idx % 13
        val fillColor =
          if (themeVars.pie(colorIdx).nonEmpty) themeVars.pie(colorIdx)
          else defaultPieColor(colorIdx)
        val yOffset = idx * (LegendRectSize + LegendSpacing)

        val legendItem = legendGroup.append("g")
        legendItem.attr("transform", s"translate(0, $yOffset)")

        val rect = legendItem.append("rect")
        rect.attr("width", LegendRectSize)
        rect.attr("height", LegendRectSize)
        rect.style("fill", fillColor)
        rect.style("stroke", themeVars.pieStrokeColor)

        val text = legendItem.append("text")
        text.attr("x", LegendRectSize + LegendSpacing)
        text.attr("y", LegendRectSize - LegendSpacing)
        text.classed("legend", true)
        val legendText = if (db.showData) {
          // Round half-up to Latin digits (ISS-1156): `"%.0f".format` followed
          // Locale.getDefault, which under CLDR providers can vary the digit set.
          s"${section.label} [${Math.round(section.value).toString}]"
        } else {
          section.label
        }
        text.text(legendText)
      }
    }

    svg.build().toMarkup()
  }

  /** Creates an SVG arc path string.
    *
    * Uses the SVG arc command to draw a slice from startAngle to endAngle.
    *
    * @param cx
    *   center X
    * @param cy
    *   center Y
    * @param radius
    *   arc radius
    * @param startAngle
    *   start angle in radians (0 = top, clockwise)
    * @param endAngle
    *   end angle in radians
    * @return
    *   SVG path d attribute value
    */
  private def createArcPath(cx: Double, cy: Double, radius: Double, startAngle: Double, endAngle: Double): String = {
    val startX = cx + radius * math.cos(startAngle - math.Pi / 2.0)
    val startY = cy + radius * math.sin(startAngle - math.Pi / 2.0)
    val endX   = cx + radius * math.cos(endAngle - math.Pi / 2.0)
    val endY   = cy + radius * math.sin(endAngle - math.Pi / 2.0)

    val largeArcFlag = if (endAngle - startAngle > math.Pi) 1 else 0

    s"M $cx $cy L $startX $startY A $radius $radius 0 $largeArcFlag 1 $endX $endY Z"
  }

  /** Default pie chart colors when theme colors are empty. */
  private def defaultPieColor(index: Int): String = {
    val colors = Array(
      "#ECECFF",
      "#ffffde",
      "#bde0fe",
      "#ffc8dd",
      "#caffbf",
      "#ffd6a5",
      "#a0c4ff",
      "#fdffb6",
      "#9bf6ff",
      "#bdb2ff",
      "#ffc6ff",
      "#e8e8e4",
      "#d4a373"
    )
    colors(index % colors.length)
  }
}
