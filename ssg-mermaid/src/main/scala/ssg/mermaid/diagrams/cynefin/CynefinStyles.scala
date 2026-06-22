/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native Cynefin framework diagram (not an upstream Mermaid diagram type).
 */
package ssg
package mermaid
package diagrams
package cynefin

import ssg.mermaid.theme.ThemeVariables

object CynefinStyles {

  def generate(vars: ThemeVariables): String =
    s""".cynefinTitle { font-size: 18px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.cynefinDomain { stroke-width: 1px; }
       |.cynefinDomainLabel { font-size: 14px; font-weight: bold; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.cynefinItem { font-size: 12px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |""".stripMargin
}
