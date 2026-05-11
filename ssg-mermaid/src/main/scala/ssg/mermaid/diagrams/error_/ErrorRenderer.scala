/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/error/errorRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package error_

import ssg.mermaid.MermaidConfig
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders an error diagram to SVG. */
object ErrorRenderer {

  def render(db: ErrorDb, config: MermaidConfig): String = {
    val viewBox = "0 0 500 80"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = ErrorStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    // Error icon (simple X in a circle)
    svg.append("circle").attr("cx", 40).attr("cy", 40).attr("r", 25).style("fill", "#ff6b6b").style("stroke", "#cc0000").style("stroke-width", "2")
    svg.append("text").attr("x", 40).attr("y", 48).attr("text-anchor", "middle").style("fill", "white").style("font-size", "28px").style("font-weight", "bold").text("!")

    // Error message
    svg.append("text").attr("x", 80).attr("y", 45).classed("errorText", true).text(db.errorMessage)

    svg.build().toMarkup()
  }
}
