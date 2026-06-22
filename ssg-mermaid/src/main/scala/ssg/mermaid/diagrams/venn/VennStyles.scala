/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native Venn diagram (not an upstream Mermaid diagram type).
 */
package ssg
package mermaid
package diagrams
package venn

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for Venn diagram elements. */
object VennStyles {

  def generate(vars: ThemeVariables): String =
    s""".vennTitle {
       |  font-size: 16px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.vennSet {
       |  stroke-width: 2px;
       |}
       |.vennSetLabel {
       |  font-size: 14px;
       |  font-weight: bold;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.vennIntersectionLabel {
       |  font-size: 12px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |""".stripMargin
}
