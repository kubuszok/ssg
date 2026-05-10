/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the Kanban diagram definition.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package kanban

import ssg.mermaid.MermaidConfig

/** Kanban board diagram type registration and rendering entry point. */
object KanbanDiagram {

  def detect(text: String): Boolean = {
    val firstLine = text.trim.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("kanban")
  }

  def parse(text: String): KanbanDb = KanbanParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    KanbanRenderer.render(db, config)
  }
}
