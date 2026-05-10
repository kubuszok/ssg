/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the Kanban diagram styles.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package kanban

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for Kanban board elements. */
object KanbanStyles {

  def generate(vars: ThemeVariables): String =
    s""".kanbanColumn {
       |  fill: ${vars.background};
       |  stroke: ${vars.lineColor};
       |  stroke-width: 1px;
       |}
       |.kanbanColumnLabel {
       |  font-size: 14px;
       |  font-weight: bold;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |.kanbanCard {
       |  fill: ${vars.mainBkg};
       |  stroke: ${vars.nodeBorder};
       |  stroke-width: 1px;
       |}
       |.kanbanCardLabel {
       |  font-size: 12px;
       |  fill: ${vars.textColor};
       |  font-family: ${vars.fontFamily};
       |}
       |""".stripMargin
}
