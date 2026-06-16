/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the Kanban diagram renderer.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package kanban

import ssg.mermaid.Accessibility
import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a Kanban board to SVG. */
object KanbanRenderer {

  private val ColumnWidth:  Double = 160.0
  private val ColumnGap:    Double = 15.0
  private val CardHeight:   Double = 35.0
  private val CardGap:      Double = 8.0
  private val HeaderHeight: Double = 30.0
  private val Padding:      Double = 20.0

  def render(db: KanbanDb, config: MermaidConfig): String = {
    val colCount  = db.columns.size.max(1)
    val maxCards  = if (db.columns.isEmpty) 0 else db.columns.map(_.cards.size).max
    val svgWidth  = colCount * (ColumnWidth + ColumnGap) + Padding * 2
    val svgHeight = HeaderHeight + maxCards * (CardHeight + CardGap) + Padding * 3

    val viewBox = s"0 0 $svgWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)
    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "kanban", db.accTitle, db.accDescription)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = KanbanStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    val mainGroup = svg.append("g")

    for ((col, colIdx) <- db.columns.zipWithIndex) {
      val x            = Padding + colIdx * (ColumnWidth + ColumnGap)
      val columnHeight = HeaderHeight + col.cards.size * (CardHeight + CardGap) + Padding

      // Column background
      val bg = mainGroup.append("rect")
      bg.attr("x", x).attr("y", Padding).attr("width", ColumnWidth).attr("height", columnHeight)
      bg.attr("rx", 6).attr("ry", 6).classed("kanbanColumn", true)

      // Column header
      mainGroup.append("text").attr("x", x + ColumnWidth / 2).attr("y", Padding + 20).attr("text-anchor", "middle").classed("kanbanColumnLabel", true).text(col.label)

      // Cards
      for ((card, cardIdx) <- col.cards.zipWithIndex) {
        val cardX = x + 5
        val cardY = Padding + HeaderHeight + cardIdx * (CardHeight + CardGap) + 5
        val cardW = ColumnWidth - 10

        mainGroup.append("rect").attr("x", cardX).attr("y", cardY).attr("width", cardW).attr("height", CardHeight).attr("rx", 4).attr("ry", 4).classed("kanbanCard", true)

        mainGroup.append("text").attr("x", cardX + cardW / 2).attr("y", cardY + CardHeight / 2 + 4).attr("text-anchor", "middle").classed("kanbanCardLabel", true).text(card.label)
      }
    }

    svg.build().toMarkup()
  }
}
