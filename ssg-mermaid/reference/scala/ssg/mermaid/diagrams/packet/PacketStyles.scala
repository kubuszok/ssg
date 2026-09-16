/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/packet/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package packet

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for packet diagram elements. */
object PacketStyles {

  def generate(vars: ThemeVariables): String =
    s""".packetTitle {
       |  font-size: 16px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.packetBitLabel {
       |  font-size: 10px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.packetField {
       |  fill: ${vars.mainBkg};
       |  stroke: ${vars.nodeBorder};
       |  stroke-width: 1px;
       |}
       |.packetFieldLabel {
       |  font-size: 12px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |""".stripMargin
}
