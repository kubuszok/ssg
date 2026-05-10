/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid event modeling diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package eventmodeling

import ssg.mermaid.parse.ParseException

/** Hand-written parser for Mermaid event modeling syntax.
  *
  * Supported syntax:
  *   - `eventmodeling` — header
  *   - `lane LaneName` — swim lane
  *   - `event id["Label"] in LaneName` — event
  *   - `command id["Label"] in LaneName` — command
  *   - `view id["Label"] in LaneName` — view/read model
  *   - `id --> id2` — flow
  */
object EventModelingParser {

  def parse(input: String): EventModelingDb = {
    val db      = new EventModelingDb
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n").map(_.trim).filter(_.nonEmpty)

    var i = 0
    while (i < lines.length && !lines(i).toLowerCase.startsWith("eventmodeling")) i += 1
    if (i >= lines.length) throw new ParseException("Expected 'eventmodeling' keyword", 1, 1)
    i += 1

    while (i < lines.length) {
      val line = lines(i).trim; i += 1
      if (line.startsWith("%%")) { /* skip */ }
      else if (line.toLowerCase.startsWith("title")) { db.title = line.substring(5).trim }
      else if (line.toLowerCase.startsWith("lane ")) { db.addLane(line.substring(5).trim) }
      else if (line.contains("-->")) {
        val parts = line.split("-->").map(_.trim)
        if (parts.length == 2) db.addFlow(parts(0), parts(1))
      } else {
        // event/command/view id["Label"] in LaneName
        val lower     = line.toLowerCase
        val eventType =
          if (lower.startsWith("command ")) "command"
          else if (lower.startsWith("view ")) "view"
          else if (lower.startsWith("event ")) "event"
          else ""
        if (eventType.nonEmpty) {
          val rest  = line.substring(eventType.length).trim
          val inIdx = rest.toLowerCase.indexOf(" in ")
          if (inIdx >= 0) {
            val idPart      = rest.substring(0, inIdx).trim
            val lane        = rest.substring(inIdx + 4).trim
            val (id, label) = parseIdLabel(idPart)
            db.addEvent(id, label, lane, eventType)
          } else {
            val (id, label) = parseIdLabel(rest)
            db.addEvent(id, label, "default", eventType)
          }
        }
      }
    }
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "")

  private def parseIdLabel(text: String): (String, String) = {
    val bracketIdx = text.indexOf('[')
    if (bracketIdx >= 0) {
      val id     = text.substring(0, bracketIdx).trim
      val endIdx = text.lastIndexOf(']')
      val raw    = if (endIdx > bracketIdx) text.substring(bracketIdx + 1, endIdx).trim else text.substring(bracketIdx + 1).trim
      val label  = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
      (if (id.nonEmpty) id else label, label)
    } else (text, text)
  }
}
