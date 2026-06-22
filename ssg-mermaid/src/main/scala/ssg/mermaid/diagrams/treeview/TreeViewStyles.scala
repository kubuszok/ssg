/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native tree view diagram (not an upstream Mermaid diagram type).
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
