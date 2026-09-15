/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/packet/packetDiagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package packet

import lowlevel.Nullable

import ssg.mermaid.MermaidConfig

/** Packet diagram type registration and rendering entry point. */
object PacketDiagram {

  def detect(text: String): Boolean = {
    val firstLine = text.trim.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("packet-beta")
  }

  def parse(text: String): PacketDb = PacketParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {
    // Diagram.ts:41-44 — pre-set the frontmatter title BEFORE parse, so an inline `title` directive
    // parsed from the body overrides it (the parser sets db.title only when an inline title is present).
    val db = new PacketDb
    title.foreach(t => db.title = t)
    PacketParser.parse(text, db)
    PacketRenderer.render(db, config)
  }
}
