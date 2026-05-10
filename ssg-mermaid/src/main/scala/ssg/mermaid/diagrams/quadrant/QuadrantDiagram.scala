/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/quadrant-chart/quadrantDiagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods
 *   Renames: quadrantDiagram.ts + quadrantDetector.ts -> QuadrantDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package quadrant

import ssg.mermaid.MermaidConfig

/** Quadrant chart diagram type registration and rendering entry point. */
object QuadrantDiagram {

  /** Detects whether the given text is a quadrant chart. */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("quadrantchart")
  }

  /** Parses quadrant chart text into a [[QuadrantDb]]. */
  def parse(text: String): QuadrantDb =
    QuadrantParser.parse(text)

  /** Renders a quadrant chart diagram from source text to SVG. */
  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    QuadrantRenderer.render(db, config)
  }
}
