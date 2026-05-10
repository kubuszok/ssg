/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sequence/sequenceDiagram.ts
 *              mermaid/packages/mermaid/src/diagrams/sequence/sequenceDetector.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces diagram registry + detector with static detection and rendering
 *   Idiom: Object with detect/parse/render methods; no dynamic module loading
 *   Renames: sequenceDiagram.ts diagram + sequenceDetector.ts → SequenceDiagram
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sequence

import ssg.mermaid.MermaidConfig

/** Sequence diagram type registration and rendering entry point.
  *
  * Combines detection (from `sequenceDetector.ts`) and rendering (from `sequenceDiagram.ts`) into a single entry point. This is the primary integration point between the Mermaid dispatch system and
  * the sequence diagram implementation.
  */
object SequenceDiagram {

  /** Detects whether the given text is a sequence diagram.
    *
    * Checks for `sequenceDiagram` keyword at the start of the cleaned text. Ports the detector from `sequenceDetector.ts`.
    *
    * @param text
    *   cleaned Mermaid diagram text (directives and comments already stripped)
    * @return
    *   true if this is a sequence diagram
    */
  def detect(text: String): Boolean = {
    val trimmed = text.trim
    """(?i)^\s*sequenceDiagram""".r.findFirstIn(trimmed).isDefined
  }

  /** Parses sequence diagram text into a [[SequenceDb]].
    *
    * @param text
    *   the raw Mermaid diagram text
    * @return
    *   a populated SequenceDb
    */
  def parse(text: String): SequenceDb =
    SequenceParser.parse(text)

  /** Renders a sequence diagram from source text to SVG.
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
    val db = parse(text)
    SequenceRenderer.render(db, config)
  }
}
