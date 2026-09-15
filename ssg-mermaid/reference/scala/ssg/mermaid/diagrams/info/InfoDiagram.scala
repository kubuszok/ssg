/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/info/infoDiagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package info

import ssg.mermaid.MermaidConfig

/** Info diagram type registration and rendering entry point. */
object InfoDiagram {

  def detect(text: String): Boolean =
    text.trim.split("[\n\r]", 2)(0).trim.toLowerCase.startsWith("info")

  def parse(text: String): InfoDb = InfoParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    InfoRenderer.render(db, config)
  }
}
