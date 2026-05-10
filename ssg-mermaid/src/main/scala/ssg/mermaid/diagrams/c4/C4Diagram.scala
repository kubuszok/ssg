/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/c4/c4Diagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package c4

import ssg.mermaid.MermaidConfig

/** C4 diagram type registration and rendering entry point. */
object C4Diagram {

  def detect(text: String): Boolean = {
    val firstLine = text.trim.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("c4context") || firstLine.startsWith("c4container") ||
    firstLine.startsWith("c4component") || firstLine.startsWith("c4deployment") ||
    firstLine.startsWith("c4dynamic")
  }

  def parse(text: String): C4Db = C4Parser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    C4Renderer.render(db, config)
  }
}
