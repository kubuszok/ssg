/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the Kanban diagram parser.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package kanban

import ssg.mermaid.parse.ParseException

/** Hand-written parser for Mermaid Kanban board syntax.
  *
  * Supported syntax:
  *   - `kanban` — header
  *   - Indentation-based: columns at indent 0, cards at indent 2+
  *   - `ColumnId[Label]` — column definition
  *   - `  CardId[Label]` — card definition (indented under column)
  */
object KanbanParser {

  def parse(input: String): KanbanDb =
    parse(input, new KanbanDb)

  /** Parses Mermaid Kanban board source text into the supplied [[KanbanDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    */
  def parse(input: String, db: KanbanDb): KanbanDb = {
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n")

    var i = 0
    // Skip to header
    while (i < lines.length && !lines(i).trim.toLowerCase.startsWith("kanban")) i += 1
    if (i >= lines.length) {
      throw new ParseException("Expected 'kanban' keyword", 1, 1)
    }
    i += 1

    // Parse columns and cards by indentation
    while (i < lines.length) {
      val line    = lines(i)
      val trimmed = line.trim
      i += 1

      if (trimmed.isEmpty || trimmed.startsWith("%%")) {
        // skip
      } else {
        val indent      = line.length - line.stripLeading().length
        val (id, label) = parseIdLabel(trimmed)
        if (indent < 2) {
          // Column
          db.addColumn(id, label)
        } else {
          // Card
          db.addCardToLast(id, label)
        }
      }
    }

    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "")

  /** Parses `id[label]` or `id["label"]` or plain `id`. */
  private def parseIdLabel(text: String): (String, String) = {
    val bracketIdx = text.indexOf('[')
    if (bracketIdx >= 0) {
      val id       = text.substring(0, bracketIdx).trim
      val endIdx   = text.lastIndexOf(']')
      val rawLabel =
        if (endIdx > bracketIdx) text.substring(bracketIdx + 1, endIdx).trim
        else text.substring(bracketIdx + 1).trim
      // Strip quotes if present
      val label = if (rawLabel.startsWith("\"") && rawLabel.endsWith("\"")) {
        rawLabel.substring(1, rawLabel.length - 1)
      } else rawLabel
      (if (id.nonEmpty) id else label, label)
    } else {
      (text, text)
    }
  }
}
