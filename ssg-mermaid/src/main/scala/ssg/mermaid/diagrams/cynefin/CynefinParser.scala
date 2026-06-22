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

  def parse(input: String): CynefinDb =
    parse(input, new CynefinDb)

  /** Parses Mermaid Cynefin diagram source text into the supplied [[CynefinDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    */
  def parse(input: String, db: CynefinDb): CynefinDb = {
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
