/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/error/errorDiagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package error_

import ssg.mermaid.MermaidConfig

/** Error diagram type registration and rendering entry point. */
object ErrorDiagram {

  def detect(text: String): Boolean =
    text.trim.split("[\n\r]", 2)(0).trim.toLowerCase.startsWith("error")

  def parse(text: String): ErrorDb = ErrorParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    ErrorRenderer.render(db, config)
  }

  /** Renders an error message as an error diagram SVG. */
  def renderError(message: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = new ErrorDb
    db.errorMessage = message
    ErrorRenderer.render(db, config)
  }
}
