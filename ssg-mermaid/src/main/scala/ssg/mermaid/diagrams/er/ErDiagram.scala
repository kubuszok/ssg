/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/er/erDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/er/erDetector.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: erDiagram.ts + erDetector.ts -> ErDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package er

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** ER diagram type registration and rendering entry point.
  *
  * Combines detection and rendering into a single entry point.
  */
object ErDiagram {

  /** Detects whether the given text is an ER diagram.
    *
    * Checks for the `erDiagram` keyword at the start of the cleaned text.
    *
    * @param text
    *   cleaned Mermaid diagram text
    * @return
    *   true if this is an ER diagram
    */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("erdiagram")
  }

  /** Parses ER diagram text into an [[ErDb]].
    *
    * @param text
    *   the raw Mermaid diagram text
    * @return
    *   a populated ErDb
    */
  def parse(text: String): ErDb =
    ErParser.parse(text)

  /** Renders an ER diagram from source text to SVG.
    *
    * @param text
    *   the raw Mermaid diagram text
    * @param config
    *   the Mermaid configuration
    * @param title
    *   the frontmatter title to apply to the db, if any (mirrors Diagram.ts:42 `db.setDiagramTitle?.(metadata.title)`)
    * @return
    *   SVG markup string
    */
  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new ErDb
    title.foreach(t => db.title = t)
    ErParser.parse(text, db)
    ErRenderer.render(db, config)
  }
}
