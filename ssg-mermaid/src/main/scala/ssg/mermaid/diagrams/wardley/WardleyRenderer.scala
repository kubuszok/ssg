/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native Wardley map diagram (not an upstream Mermaid diagram type).
 */
package ssg
package mermaid
package diagrams
package wardley

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders a Wardley map to SVG.
  *
  * X-axis: Evolution (Genesis -> Custom Built -> Product -> Commodity) Y-axis: Visibility (Invisible -> Visible)
  */
object WardleyRenderer {

  private val Padding:         Double        = 50.0
  private val ChartWidth:      Double        = 600.0
  private val ChartHeight:     Double        = 400.0
  private val EvolutionLabels: Array[String] = Array("Genesis", "Custom Built", "Product", "Commodity")

  def render(db: WardleyDb, config: MermaidConfig): String = {
    val svgWidth  = ChartWidth + Padding * 3
    val svgHeight = ChartHeight + Padding * 3 + (if (db.title.nonEmpty) 30 else 0)
    val viewBox   = s"0 0 $svgWidth $svgHeight"
    val svg       = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "wardley", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = WardleyStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    val mainGroup = svg.append("g")
    var yOff      = Padding

    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", svgWidth / 2).attr("y", 25).attr("text-anchor", "middle").classed("wardleyTitle", true).text(db.title)
      yOff += 30
    }

    val chartX = Padding * 2; val chartY = yOff

    // Background
    mainGroup.append("rect").attr("x", chartX).attr("y", chartY).attr("width", ChartWidth).attr("height", ChartHeight).style("fill", "#fafafa").style("stroke", "#ccc")

    // Evolution axis labels
    for ((label, idx) <- EvolutionLabels.zipWithIndex) {
      val x = chartX + (idx + 0.5) * ChartWidth / 4
      mainGroup.append("text").attr("x", x).attr("y", chartY + ChartHeight + 20).attr("text-anchor", "middle").classed("wardleyAxisLabel", true).text(label)

      // Vertical divider
      if (idx > 0) {
        val dx = chartX + idx * ChartWidth / 4
        mainGroup.append("line").attr("x1", dx).attr("y1", chartY).attr("x2", dx).attr("y2", chartY + ChartHeight).style("stroke", "#ddd").style("stroke-dasharray", "3,3")
      }
    }

    // Y-axis labels
    mainGroup.append("text").attr("x", chartX - 10).attr("y", chartY + 10).attr("text-anchor", "end").classed("wardleyAxisLabel", true).text("Visible")
    mainGroup.append("text").attr("x", chartX - 10).attr("y", chartY + ChartHeight).attr("text-anchor", "end").classed("wardleyAxisLabel", true).text("Invisible")

    // Plot components
    val positions = mutable.Map.empty[String, (Double, Double)]
    for (comp <- db.components) {
      val x = chartX + comp.evolution * ChartWidth
      val y = chartY + (1.0 - comp.visibility) * ChartHeight
      positions(comp.name) = (x, y)

      mainGroup.append("circle").attr("cx", x).attr("cy", y).attr("r", 6).classed("wardleyComponent", true)
      mainGroup.append("text").attr("x", x + 10).attr("y", y + 4).classed("wardleyComponentLabel", true).text(comp.name)
    }

    // Draw links
    for (link <- db.links)
      for {
        (sx, sy) <- positions.get(link.from)
        (tx, ty) <- positions.get(link.to)
      }
        mainGroup.append("line").attr("x1", sx).attr("y1", sy).attr("x2", tx).attr("y2", ty).classed("wardleyLink", true)

    svg.build().toMarkup()
  }
}
