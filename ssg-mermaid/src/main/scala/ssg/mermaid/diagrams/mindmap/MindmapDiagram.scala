/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/mindmap/mindmapDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/mindmap/mindmapDetector.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: mindmapDiagram.ts + mindmapDetector.ts -> MindmapDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package mindmap

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Mindmap diagram type registration and rendering entry point. */
object MindmapDiagram {

  /** Detects whether the given text is a mindmap diagram. */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("mindmap")
  }

  /** Parses mindmap text into a [[MindmapDb]]. */
  def parse(text: String): MindmapDb =
    MindmapParser.parse(text)

  /** Renders a mindmap diagram from source text to SVG. */
  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new MindmapDb
    title.foreach(t => db.title = t)
    MindmapParser.parse(text, db)
    MindmapRenderer.render(db, config)
  }
}
