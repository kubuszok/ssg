/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/block/blockDiagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods
 *   Renames: blockDiagram.ts -> BlockDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package block

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Block diagram type registration and rendering entry point. */
object BlockDiagram {

  /** Detects whether the given text is a block diagram. */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("block-beta")
  }

  /** Parses block diagram text into a [[BlockDb]]. */
  def parse(text: String): BlockDb =
    BlockParser.parse(text)

  /** Renders a block diagram from source text to SVG. */
  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new BlockDb
    title.foreach(t => db.title = t)
    BlockParser.parse(text, db)
    BlockRenderer.render(db, config)
  }
}
