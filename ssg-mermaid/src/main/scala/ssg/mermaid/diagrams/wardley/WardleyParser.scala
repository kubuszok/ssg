/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid wardley map
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package wardley

import ssg.mermaid.parse.ParseException

/** Hand-written parser for Mermaid Wardley map syntax.
  *
  * Supported syntax:
  *   - `wardley` — header
  *   - `title "My Map"` — title
  *   - `component Name [visibility, evolution]` — component placement
  *   - `Name --> OtherName` — dependency link
  */
object WardleyParser {

  def parse(input: String): WardleyDb = {
    val db      = new WardleyDb
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n").map(_.trim).filter(_.nonEmpty)

    var i = 0
    while (i < lines.length && !lines(i).toLowerCase.startsWith("wardley")) i += 1
    if (i >= lines.length) throw new ParseException("Expected 'wardley' keyword", 1, 1)
    i += 1

    while (i < lines.length) {
      val line = lines(i).trim; i += 1
      if (line.startsWith("%%")) { /* skip */ }
      else if (line.toLowerCase.startsWith("title")) { db.title = line.substring(5).trim }
      else if (line.toLowerCase.startsWith("component ")) {
        val rest       = line.substring(10).trim
        val bracketIdx = rest.indexOf('[')
        if (bracketIdx >= 0) {
          val name       = rest.substring(0, bracketIdx).trim
          val endIdx     = rest.indexOf(']', bracketIdx)
          val coords     = if (endIdx > bracketIdx) rest.substring(bracketIdx + 1, endIdx) else ""
          val parts      = coords.split(",").map(_.trim)
          val visibility = parts.headOption
            .flatMap(s =>
              try Some(s.toDouble)
              catch { case _: NumberFormatException => None }
            )
            .getOrElse(0.5)
          val evolution = parts
            .lift(1)
            .flatMap(s =>
              try Some(s.toDouble)
              catch { case _: NumberFormatException => None }
            )
            .getOrElse(0.5)
          db.addComponent(name, visibility, evolution)
        } else {
          db.addComponent(rest, 0.5, 0.5)
        }
      } else if (line.contains("-->")) {
        val parts = line.split("-->").map(_.trim)
        if (parts.length == 2) db.addLink(parts(0), parts(1))
      }
    }
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "")
}
