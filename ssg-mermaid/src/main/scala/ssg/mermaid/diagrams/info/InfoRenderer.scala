/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/info/infoRenderer.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package info

import ssg.mermaid.MermaidConfig
import ssg.mermaid.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

/** Renders an info (version display) diagram to SVG. */
object InfoRenderer {

  def render(db: InfoDb, config: MermaidConfig): String = {
    val viewBox = "0 0 300 50"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img"); svg.classed("mermaid", true)

    val defs      = svg.append("defs")
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)
    val css       = InfoStyles.generate(themeVars)
    defs.append("style").attr("type", "text/css").text(CssGenerator.generateBaseStyles(themeVars) + "\n" + css)

    svg.append("text").attr("x", 150).attr("y", 30).attr("text-anchor", "middle").classed("infoText", true).text(s"mermaid version ${db.version}")

    svg.build().toMarkup()
  }
}
