/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid ishikawa (fishbone) diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package ishikawa

import ssg.mermaid.MermaidConfig

/** Ishikawa (fishbone/cause-and-effect) diagram type registration and rendering entry point. */
object IshikawaDiagram {

  def detect(text: String): Boolean = {
    val firstLine = text.trim.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("ishikawa")
  }

  def parse(text: String): IshikawaDb = IshikawaParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    IshikawaRenderer.render(db, config)
  }
}
