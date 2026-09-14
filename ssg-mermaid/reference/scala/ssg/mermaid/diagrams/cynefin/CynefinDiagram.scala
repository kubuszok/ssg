/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native Cynefin framework diagram (not an upstream Mermaid diagram type).
 */
package ssg
package mermaid
package diagrams
package cynefin

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Cynefin framework diagram type registration and rendering entry point. */
object CynefinDiagram {

  def detect(text: String): Boolean =
    text.trim.split("[\n\r]", 2)(0).trim.toLowerCase.startsWith("cynefin")

  def parse(text: String): CynefinDb = CynefinParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new CynefinDb
    title.foreach(t => db.title = t)
    CynefinParser.parse(text, db)
    CynefinRenderer.render(db, config)
  }
}
