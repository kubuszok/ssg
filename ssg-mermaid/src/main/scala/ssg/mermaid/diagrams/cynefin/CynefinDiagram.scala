/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid cynefin framework diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package cynefin

import ssg.mermaid.MermaidConfig

/** Cynefin framework diagram type registration and rendering entry point. */
object CynefinDiagram {

  def detect(text: String): Boolean =
    text.trim.split("[\n\r]", 2)(0).trim.toLowerCase.startsWith("cynefin")

  def parse(text: String): CynefinDb = CynefinParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    CynefinRenderer.render(db, config)
  }
}
