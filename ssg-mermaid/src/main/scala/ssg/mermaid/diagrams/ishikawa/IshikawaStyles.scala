/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid ishikawa (fishbone) diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package ishikawa

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for Ishikawa diagram elements. */
object IshikawaStyles {

  def generate(vars: ThemeVariables): String =
    s""".ishikawaSpine {
       |  stroke: ${vars.lineColor};
       |  stroke-width: 3px;
       |}
       |.ishikawaEffect {
       |  font-size: 16px;
       |  font-weight: bold;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.ishikawaBranch {
       |  stroke: ${vars.lineColor};
       |  stroke-width: 2px;
       |}
       |.ishikawaBranchLabel {
       |  font-size: 14px;
       |  font-weight: bold;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.ishikawaCause {
       |  stroke: ${vars.lineColor};
       |  stroke-width: 1px;
       |}
       |.ishikawaCauseLabel {
       |  font-size: 11px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |""".stripMargin
}
