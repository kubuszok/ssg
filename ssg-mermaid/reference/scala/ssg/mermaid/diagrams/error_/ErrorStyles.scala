/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/error/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package error_

import ssg.mermaid.theme.ThemeVariables

object ErrorStyles {

  def generate(vars: ThemeVariables): String =
    s""".errorText { font-size: 14px; fill: #cc0000; font-family: ${vars.fontFamily}; }
       |""".stripMargin
}
