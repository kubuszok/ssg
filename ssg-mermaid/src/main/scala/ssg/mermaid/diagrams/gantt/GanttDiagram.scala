/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/gantt/ganttDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/gantt/ganttDetector.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: ganttDiagram.ts + ganttDetector.ts -> GanttDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package gantt

import ssg.mermaid.MermaidConfig

/** Gantt chart diagram type registration and rendering entry point.
  *
  * Combines detection and rendering into a single entry point.
  */
object GanttDiagram {

  /** Detects whether the given text is a Gantt chart.
    *
    * @param text
    *   cleaned Mermaid diagram text
    * @return
    *   true if this is a Gantt chart
    */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("gantt")
  }

  /** Parses Gantt chart text into a [[GanttDb]].
    *
    * @param text
    *   the raw Mermaid diagram text
    * @return
    *   a populated GanttDb
    */
  def parse(text: String): GanttDb =
    GanttParser.parse(text)

  /** Renders a Gantt chart from source text to SVG.
    *
    * @param text
    *   the raw Mermaid diagram text
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    GanttRenderer.render(db, config)
  }
}
