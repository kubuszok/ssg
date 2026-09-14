/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sankey/sankeyDiagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods
 *   Renames: sankeyDiagram.ts -> SankeyDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sankey

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Sankey diagram type registration and rendering entry point. */
object SankeyDiagram {

  /** Detects whether the given text is a Sankey diagram. */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("sankey-beta")
  }

  /** Parses Sankey diagram text into a [[SankeyDb]]. */
  def parse(text: String): SankeyDb =
    SankeyParser.parse(text)

  /** Renders a Sankey diagram from source text to SVG. */
  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new SankeyDb
    title.foreach(t => db.title = t)
    SankeyParser.parse(text, db)
    SankeyRenderer.render(db, config)
  }
}
