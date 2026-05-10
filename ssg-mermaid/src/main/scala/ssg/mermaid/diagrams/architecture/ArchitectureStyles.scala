/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the architecture diagram styles.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package architecture

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for architecture diagram elements. */
object ArchitectureStyles {

  def generate(vars: ThemeVariables): String =
    s""".archService {
       |  fill: ${vars.mainBkg};
       |  stroke: ${vars.nodeBorder};
       |  stroke-width: 1px;
       |}
       |.archJunction {
       |  fill: ${vars.lineColor};
       |  stroke: none;
       |}
       |.archLabel {
       |  font-size: 12px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.archEdge {
       |  stroke: ${vars.lineColor};
       |  stroke-width: 1.5px;
       |}
       |.archEdgeLabel {
       |  font-size: 10px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |""".stripMargin
}
