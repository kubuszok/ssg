/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/state/stateDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/state/stateDetector-v2.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: stateDiagram.ts + stateDetector-v2.ts -> StateDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package state

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** State diagram type registration and rendering entry point.
  *
  * Combines detection and rendering into a single entry point.
  */
object StateDiagram {

  /** Detects whether the given text is a state diagram.
    *
    * Checks for `stateDiagram` or `stateDiagram-v2` keywords at the start of the cleaned text.
    *
    * @param text
    *   cleaned Mermaid diagram text
    * @return
    *   true if this is a state diagram
    */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("statediagram")
  }

  /** Parses state diagram text into a [[StateDb]].
    *
    * @param text
    *   the raw Mermaid diagram text
    * @return
    *   a populated StateDb
    */
  def parse(text: String): StateDb =
    StateParser.parse(text)

  /** Renders a state diagram from source text to SVG.
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
    val db = new StateDb
    title.foreach(t => db.title = t)
    StateParser.parse(text, db)
    StateRenderer.render(db, config)
  }
}
