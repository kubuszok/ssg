/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/info/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package info

import ssg.mermaid.theme.ThemeVariables

object InfoStyles {

  def generate(vars: ThemeVariables): String =
    s""".infoText { font-size: 16px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |""".stripMargin
}
