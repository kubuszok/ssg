/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/user-journey/journeyDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/user-journey/journeyDetector.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: journeyDiagram.ts + journeyDetector.ts -> JourneyDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package journey

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** User journey diagram type registration and rendering entry point. */
object JourneyDiagram {

  /** Detects whether the given text is a user journey diagram. */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("journey")
  }

  /** Parses user journey text into a [[JourneyDb]]. */
  def parse(text: String): JourneyDb =
    JourneyParser.parse(text)

  /** Renders a user journey diagram from source text to SVG. */
  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new JourneyDb
    title.foreach(t => db.title = t)
    JourneyParser.parse(text, db)
    JourneyRenderer.render(db, config)
  }
}
