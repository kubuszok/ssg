/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid event modeling diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package eventmodeling

import ssg.mermaid.theme.ThemeVariables

object EventModelingStyles {

  def generate(vars: ThemeVariables): String =
    s""".emTitle { font-size: 16px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.emLaneLabel { font-size: 12px; font-weight: bold; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.emEvent { stroke-width: 1px; }
       |.emEventLabel { font-size: 11px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.emFlow { stroke: ${vars.lineColor}; stroke-width: 1.5px; }
       |""".stripMargin
}
