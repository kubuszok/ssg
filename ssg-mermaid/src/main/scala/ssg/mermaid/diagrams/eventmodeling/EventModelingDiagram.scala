/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native event modeling diagram (not an upstream Mermaid diagram type).
 */
package ssg
package mermaid
package diagrams
package eventmodeling

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Event modeling diagram type registration and rendering entry point. */
object EventModelingDiagram {

  def detect(text: String): Boolean =
    text.trim.split("[\n\r]", 2)(0).trim.toLowerCase.startsWith("eventmodeling")

  def parse(text: String): EventModelingDb = EventModelingParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new EventModelingDb
    title.foreach(t => db.title = t)
    EventModelingParser.parse(text, db)
    EventModelingRenderer.render(db, config)
  }
}
