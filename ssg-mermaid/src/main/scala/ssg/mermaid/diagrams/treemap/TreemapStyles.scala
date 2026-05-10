/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid treemap diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package treemap

import ssg.mermaid.theme.ThemeVariables

object TreemapStyles {

  def generate(vars: ThemeVariables): String =
    s""".treemapTitle { font-size: 16px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.treemapCell { opacity: 0.9; }
       |.treemapLabel { font-size: 12px; fill: white; font-family: ${vars.fontFamily}; }
       |""".stripMargin
}
