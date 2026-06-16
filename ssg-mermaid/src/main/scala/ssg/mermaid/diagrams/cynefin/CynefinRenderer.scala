/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid cynefin framework diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package cynefin

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a Cynefin framework diagram to SVG. */
object CynefinRenderer {

  private val Size:         Double              = 500.0
  private val Padding:      Double              = 30.0
  private val DomainColors: Map[String, String] = Map(
    "complex" -> "#e8d5f5",
    "complicated" -> "#d5e8f5",
    "clear" -> "#d5f5e8",
    "chaotic" -> "#f5e8d5",
    "obvious" -> "#d5f5e8",
    "disorder" -> "#f5f5d5"
  )

  def render(db: CynefinDb, config: MermaidConfig): String = {
    val svgSize = Size + Padding * 2 + 40
    val viewBox = s"0 0 $svgSize $svgSize"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "cynefin", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = CynefinStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    val styleEl   = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    val mainGroup = svg.append("g")
    val half      = Size / 2

    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", svgSize / 2).attr("y", 25).attr("text-anchor", "middle").classed("cynefinTitle", true).text(db.title)
    }

    val ox = Padding; val oy = Padding + 20

    // Four quadrants
    val domains = Seq(
      ("Complex", ox, oy, half, half),
      ("Complicated", ox + half, oy, half, half),
      ("Chaotic", ox, oy + half, half, half),
      ("Clear", ox + half, oy + half, half, half)
    )

    for ((name, x, y, w, h) <- domains) {
      val color = DomainColors.getOrElse(name.toLowerCase, "#f0f0f0")
      mainGroup.append("rect").attr("x", x).attr("y", y).attr("width", w).attr("height", h).style("fill", color).style("stroke", "#ccc").classed("cynefinDomain", true)

      mainGroup.append("text").attr("x", x + w / 2).attr("y", y + 20).attr("text-anchor", "middle").classed("cynefinDomainLabel", true).text(name)

      // Items in this domain
      val domainItems = db.itemsInDomain(name)
      for ((item, idx) <- domainItems.zipWithIndex)
        mainGroup.append("text").attr("x", x + w / 2).attr("y", y + 45 + idx * 20).attr("text-anchor", "middle").classed("cynefinItem", true).text(item.label)
    }

    // Center: Disorder
    val centerSize = 80.0
    mainGroup
      .append("rect")
      .attr("x", ox + half - centerSize / 2)
      .attr("y", oy + half - centerSize / 2)
      .attr("width", centerSize)
      .attr("height", centerSize)
      .style("fill", DomainColors("disorder"))
      .style("stroke", "#999")
      .classed("cynefinDomain", true)
    mainGroup.append("text").attr("x", ox + half).attr("y", oy + half + 5).attr("text-anchor", "middle").classed("cynefinDomainLabel", true).text("Disorder")

    svg.build().toMarkup()
  }
}
