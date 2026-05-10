/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid wardley map
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package wardley

import ssg.mermaid.MermaidConfig

object WardleyDiagram {

  def detect(text: String): Boolean =
    text.trim.split("[\n\r]", 2)(0).trim.toLowerCase.startsWith("wardley")

  def parse(text: String): WardleyDb = WardleyParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    WardleyRenderer.render(db, config)
  }
}
