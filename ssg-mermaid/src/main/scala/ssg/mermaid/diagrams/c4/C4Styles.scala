/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/c4/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package c4

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for C4 diagram elements. */
object C4Styles {

  def generate(vars: ThemeVariables): String =
    s""".c4Title {
       |  font-size: 18px; fill: ${vars.textColor}; font-family: ${vars.fontFamily};
       |}
       |.c4Person { fill: #08427B; stroke: #073B6F; }
       |.c4PersonBody { fill: #08427B; stroke: #073B6F; }
       |.c4System { fill: #1168BD; stroke: #0B4884; stroke-width: 1px; }
       |.c4Container { fill: #438DD5; stroke: #3C7FC0; stroke-width: 1px; }
       |.c4Component { fill: #85BBF0; stroke: #78A8D8; stroke-width: 1px; }
       |.c4Label { font-size: 14px; fill: white; font-family: ${vars.fontFamily}; font-weight: bold; }
       |.c4TypeLabel { font-size: 10px; fill: white; font-family: ${vars.fontFamily}; font-style: italic; }
       |.c4Desc { font-size: 10px; fill: white; font-family: ${vars.fontFamily}; }
       |.c4Tech { font-size: 10px; fill: white; font-family: ${vars.fontFamily}; font-style: italic; }
       |.c4Rel { stroke: ${vars.lineColor}; stroke-width: 1.5px; stroke-dasharray: 5,5; }
       |.c4RelLabel { font-size: 10px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |""".stripMargin
}
