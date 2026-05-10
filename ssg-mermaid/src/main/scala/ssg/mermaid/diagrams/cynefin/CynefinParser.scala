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

import ssg.mermaid.parse.ParseException

/** Hand-written parser for Mermaid Cynefin diagram syntax.
  *
  * Supported syntax:
  *   - `cynefin` — header
  *   - `Complex: item1, item2` — items in a domain
  *   - `Complicated: item3` — items in another domain
  *   - `Clear: item4` — items in clear domain
  *   - `Chaotic: item5` — items in chaotic domain
  *   - `Disorder: item6` — items in disorder (center)
  */
object CynefinParser {

  def parse(input: String): CynefinDb = {
    val db      = new CynefinDb
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n").map(_.trim).filter(_.nonEmpty)

    var i = 0
    while (i < lines.length && !lines(i).toLowerCase.startsWith("cynefin")) i += 1
    if (i >= lines.length) throw new ParseException("Expected 'cynefin' keyword", 1, 1)
    i += 1

    while (i < lines.length) {
      val line = lines(i).trim; i += 1
      if (line.startsWith("%%")) {
        // skip
      } else if (line.toLowerCase.startsWith("title")) {
        db.title = line.substring(5).trim
      } else {
        // Parse domain: items
        val colonIdx = line.indexOf(':')
        if (colonIdx > 0) {
          val domain   = line.substring(0, colonIdx).trim
          val itemsStr = line.substring(colonIdx + 1).trim
          val itemList = itemsStr.split(",").map(_.trim).filter(_.nonEmpty)
          for (item <- itemList)
            db.addItem(item, domain)
        }
      }
    }
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "")
}
