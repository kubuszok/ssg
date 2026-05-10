/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/git/gitGraphDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/git/gitGraphDetector.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: gitGraphDiagram.ts + gitGraphDetector.ts -> GitDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package git

import ssg.mermaid.MermaidConfig

/** Git graph diagram type registration and rendering entry point. */
object GitDiagram {

  /** Detects whether the given text is a git graph diagram. */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("gitgraph")
  }

  /** Parses git graph text into a [[GitDb]]. */
  def parse(text: String): GitDb =
    GitParser.parse(text)

  /** Renders a git graph diagram from source text to SVG. */
  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    GitRenderer.render(db, config)
  }
}
