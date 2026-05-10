/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid cynefin framework diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
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
