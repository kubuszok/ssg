/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/pie/pieDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/pie/pieDetector.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: pieDiagram.ts + pieDetector.ts -> PieDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package pie

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Pie chart diagram type registration and rendering entry point.
  *
  * Combines detection (from `pieDetector.ts`) and rendering (from `pieDiagram.ts`) into a single entry point.
  */
object PieDiagram {

  /** Detects whether the given text is a pie chart diagram.
    *
    * Checks for the `pie` keyword at the start of the cleaned text.
    *
    * @param text
    *   cleaned Mermaid diagram text (directives and comments already stripped)
    * @return
    *   true if this is a pie chart diagram
    */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("pie")
  }

  /** Parses pie chart text into a [[PieDb]].
    *
    * @param text
    *   the raw Mermaid diagram text
    * @return
    *   a populated PieDb
    */
  def parse(text: String): PieDb =
    PieParser.parse(text)

  /** Renders a pie chart diagram from source text to SVG.
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
    val db = new PieDb
    title.foreach(t => db.title = t)
    PieParser.parse(text, db)
    PieRenderer.render(db, config)
  }
}
