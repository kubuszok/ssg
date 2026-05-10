/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid tree view diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package treeview

import ssg.mermaid.theme.ThemeVariables

object TreeViewStyles {

  def generate(vars: ThemeVariables): String =
    s""".treeTitle { font-size: 16px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |.treeNode { fill: ${vars.primaryColor}; stroke: ${vars.primaryBorderColor}; }
       |.treeConnector { stroke: ${vars.lineColor}; stroke-width: 1px; }
       |.treeLabel { font-size: 12px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }
       |""".stripMargin
}
