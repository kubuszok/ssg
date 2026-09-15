/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the radar chart styles.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package radar

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for radar chart elements. */
object RadarStyles {

  def generate(vars: ThemeVariables): String =
    s""".radarGrid {
       |  fill: none;
       |  stroke: ${vars.lineColor};
       |  stroke-width: 0.5px;
       |  opacity: 0.3;
       |}
       |.radarAxis {
       |  stroke: ${vars.lineColor};
       |  stroke-width: 1px;
       |}
       |.radarAxisLabel {
       |  font-size: 12px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.radarSeries {
       |  stroke-linejoin: round;
       |}
       |.radarPoint {
       |  stroke: white;
       |  stroke-width: 1px;
       |}
       |.radarTitle {
       |  font-size: 16px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |""".stripMargin
}
