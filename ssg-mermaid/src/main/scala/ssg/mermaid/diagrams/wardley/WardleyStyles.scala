/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid wardley map
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package wardley

import ssg.mermaid.theme.ThemeVariables

object WardleyStyles {

  def generate(vars: ThemeVariables): String =
    s""".wardleyTitle { font-size: 16px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.wardleyAxisLabel { font-size: 11px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.wardleyComponent { fill: ${vars.primaryColor}; stroke: ${vars.primaryBorderColor}; stroke-width: 1px; }
       |.wardleyComponentLabel { font-size: 12px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.wardleyLink { stroke: ${vars.lineColor}; stroke-width: 1px; }
       |""".stripMargin
}
