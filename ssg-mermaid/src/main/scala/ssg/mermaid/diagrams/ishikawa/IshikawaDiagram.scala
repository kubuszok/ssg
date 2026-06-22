/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native Ishikawa (fishbone) diagram (not an upstream Mermaid diagram type).
 */
package ssg
package mermaid
package diagrams
package ishikawa

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Ishikawa (fishbone/cause-and-effect) diagram type registration and rendering entry point. */
object IshikawaDiagram {

  def detect(text: String): Boolean = {
    val firstLine = text.trim.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("ishikawa")
  }

  def parse(text: String): IshikawaDb = IshikawaParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new IshikawaDb
    title.foreach(t => db.title = t)
    IshikawaParser.parse(text, db)
    IshikawaRenderer.render(db, config)
  }
}
