/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/requirement/requirementDiagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods
 *   Renames: requirementDiagram.ts -> RequirementDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package requirement

import ssg.mermaid.MermaidConfig

/** Requirement diagram type registration and rendering entry point. */
object RequirementDiagram {

  /** Detects whether the given text is a requirement diagram. */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("requirementdiagram")
  }

  /** Parses requirement diagram text into a [[RequirementDb]]. */
  def parse(text: String): RequirementDb =
    RequirementParser.parse(text)

  /** Renders a requirement diagram from source text to SVG. */
  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    RequirementRenderer.render(db, config)
  }
}
