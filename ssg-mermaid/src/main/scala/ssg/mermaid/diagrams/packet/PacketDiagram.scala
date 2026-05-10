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

import ssg.mermaid.MermaidConfig

/** Packet diagram type registration and rendering entry point. */
object PacketDiagram {

  def detect(text: String): Boolean = {
    val firstLine = text.trim.split("[\n\r]", 2)(0).trim.toLowerCase
    firstLine.startsWith("packet-beta")
  }

  def parse(text: String): PacketDb = PacketParser.parse(text)

  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {
    val db = parse(text)
    PacketRenderer.render(db, config)
  }
}
