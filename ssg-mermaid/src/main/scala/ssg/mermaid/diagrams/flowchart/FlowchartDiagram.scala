/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/flowchart/flowDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/flowchart/flowDetector-v2.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: flowDiagram.ts diagram + flowDetector-v2.ts → FlowchartDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package flowchart

import ssg.mermaid.MermaidConfig

/** Flowchart diagram type registration and rendering entry point.
  *
  * Combines detection (from `flowDetector-v2.ts`) and rendering (from `flowDiagram.ts`) into a single entry point. This is the primary integration point between the Mermaid dispatch system and the
  * flowchart implementation.
  */
object FlowchartDiagram {

  /** Detects whether the given text is a flowchart diagram.
    *
    * Checks for `graph` or `flowchart` keywords at the start of the cleaned text. Ports the detector from `flowDetector-v2.ts`.
    *
    * @param text
    *   cleaned Mermaid diagram text (directives and comments already stripped)
    * @return
    *   true if this is a flowchart diagram
    */
  def detect(text: String): Boolean = {
    val trimmed   = text.trim
    val firstLine = trimmed.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("graph") || firstLine.startsWith("flowchart")
  }

  /** Parses flowchart text into a [[FlowchartDb]].
    *
    * @param text
    *   the raw Mermaid diagram text
    * @return
    *   a populated FlowchartDb
    */
  def parse(text: String): FlowchartDb =
    FlowchartParser.parse(text)

  /** Renders a flowchart diagram from source text to SVG.
    *
    * This is the main entry point: parses the text, builds the data model, runs layout, and generates SVG.
    *
    * @param text
    *   the raw Mermaid diagram text
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    // flowDb.ts:20 — `let config = getConfig()` makes config available before parsing;
    // set config-driven Db fields before the parser adds edges so the limit is live.
    val db = new FlowchartDb
    db.maxEdges = config.maxEdges
    FlowchartParser.parse(text, db)
    FlowchartRenderer.render(db, config)
  }
}
