/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/packet/packetRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package packet

import ssg.mermaid.MermaidConfig
import ssg.mermaid.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders a packet diagram to SVG. */
object PacketRenderer {

  private val Padding:   Double = 20.0
  private val RowHeight: Double = 40.0
  private val BitWidth:  Double = 20.0

  def render(db: PacketDb, config: MermaidConfig): String = {
    val bitsPerRow = db.bitsPerRow
    val totalWidth = bitsPerRow * BitWidth + Padding * 2

    // Determine how many rows we need
    val maxBit =
      if (db.fields.isEmpty) bitsPerRow
      else db.fields.map(_.endBit).max + 1
    val numRows   = math.max(1, math.ceil(maxBit.toDouble / bitsPerRow).toInt)
    val svgHeight = numRows * RowHeight + Padding * 3 + (if (db.title.nonEmpty) 30 else 0)

    val viewBox = s"0 0 $totalWidth $svgHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = PacketStyles.generate(themeVars)
    val baseCss   = CssGenerator.generateBaseStyles(themeVars)
    defs.append("style").attr("type", "text/css").text(baseCss + "\n" + css)

    val mainGroup = svg.append("g")

    var yOffset = Padding
    if (db.title.nonEmpty) {
      mainGroup.append("text").attr("x", totalWidth / 2).attr("y", yOffset + 15).attr("text-anchor", "middle").classed("packetTitle", true).text(db.title)
      yOffset += 30
    }

    // Draw bit number headers
    for (bit <- 0 until bitsPerRow by 8)
      mainGroup.append("text").attr("x", Padding + bit * BitWidth + BitWidth / 2).attr("y", yOffset + 12).attr("text-anchor", "middle").classed("packetBitLabel", true).text(bit.toString)
    yOffset += 15

    // Draw fields
    for (field <- db.fields) {
      val startRow = field.startBit / bitsPerRow
      val endRow   = field.endBit / bitsPerRow

      for (row <- startRow to endRow) {
        val rowStartBit = if (row == startRow) field.startBit % bitsPerRow else 0
        val rowEndBit   = if (row == endRow) field.endBit % bitsPerRow else bitsPerRow - 1
        val x           = Padding + rowStartBit * BitWidth
        val y           = yOffset + row * RowHeight
        val w           = (rowEndBit - rowStartBit + 1) * BitWidth
        val h           = RowHeight - 2

        val rect = mainGroup.append("rect")
        rect.attr("x", x).attr("y", y).attr("width", w).attr("height", h)
        rect.classed("packetField", true)

        val label = mainGroup.append("text")
        label.attr("x", x + w / 2).attr("y", y + h / 2 + 5)
        label.attr("text-anchor", "middle").classed("packetFieldLabel", true)
        label.text(field.label)
      }
    }

    svg.build().toMarkup()
  }
}
