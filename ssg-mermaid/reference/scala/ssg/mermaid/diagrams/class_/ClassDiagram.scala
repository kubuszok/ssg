/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/class/classDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/class/classDetector.ts
 *              mermaid/packages/mermaid/src/diagrams/class/classDetector-V2.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: classDiagram.ts diagram + classDetector.ts → ClassDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package class_

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Class diagram type registration and rendering entry point.
  *
  * Combines detection (from `classDetector.ts` / `classDetector-V2.ts`) and rendering (from `classDiagram.ts`) into a single entry point.
  */
object ClassDiagram {

  /** Detects whether the given text is a class diagram.
    *
    * Checks for `classDiagram` keyword at the start of the cleaned text. Ports the detector from `classDetector.ts` / `classDetector-V2.ts`.
    *
    * @param text
    *   cleaned Mermaid diagram text (directives and comments already stripped)
    * @return
    *   true if this is a class diagram
    */
  def detect(text: String): Boolean = {
    val trimmed = text.trim
    """(?i)^\s*classDiagram""".r.findFirstIn(trimmed).isDefined
  }

  /** Parses class diagram text into a [[ClassDb]].
    *
    * @param text
    *   the raw Mermaid diagram text
    * @return
    *   a populated ClassDb
    */
  def parse(text: String): ClassDb =
    ClassParser.parse(text)

  /** Renders a class diagram from source text to SVG.
    *
    * This is the main entry point: parses the text, builds the data model, runs layout, and generates SVG.
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
    val db = new ClassDb
    // classDb.ts:267 — setLink consults config.securityLevel; thread the active level into the db
    // before parse so click-link parsing sees the right level (mirrors FlowchartDiagram.scala:81).
    db.securityLevel = config.securityLevel
    title.foreach(t => db.title = t)
    ClassParser.parse(text, db)
    ClassRenderer.render(db, config)
  }
}
