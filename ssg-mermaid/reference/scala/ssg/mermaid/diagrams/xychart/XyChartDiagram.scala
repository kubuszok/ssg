/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/xychart/xychartDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/xychart/xychartDetector.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: xychartDiagram.ts + xychartDetector.ts -> XyChartDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package xychart

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** XY chart diagram type registration and rendering entry point. */
object XyChartDiagram {

  /** Detects whether the given text is an XY chart diagram. */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("xychart-beta")
  }

  /** Parses XY chart text into an [[XyChartDb]]. */
  def parse(text: String): XyChartDb =
    XyChartParser.parse(text)

  /** Renders an XY chart diagram from source text to SVG. */
  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new XyChartDb
    title.foreach(t => db.title = t)
    XyChartParser.parse(text, db)
    XyChartRenderer.render(db, config)
  }
}
