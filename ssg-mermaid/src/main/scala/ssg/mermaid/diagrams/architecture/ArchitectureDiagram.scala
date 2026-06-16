/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the architecture diagram definition.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package architecture

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Architecture diagram type registration and rendering entry point. */
object ArchitectureDiagram {

  def detect(text: String): Boolean = {
    val firstLine = text.trim.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("architecture-beta")
  }

  def parse(text: String): ArchitectureDb = ArchitectureParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new ArchitectureDb
    title.foreach(t => db.title = t)
    ArchitectureParser.parse(text, db)
    ArchitectureRenderer.render(db, config)
  }
}
