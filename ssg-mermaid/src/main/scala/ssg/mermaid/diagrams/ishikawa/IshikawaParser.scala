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

import ssg.mermaid.parse.ParseException

/** Hand-written parser for Mermaid Ishikawa (fishbone) syntax.
  *
  * Supported syntax:
  *   - `ishikawa` — header
  *   - `effect["Label"]` — the effect (fish head)
  *   - `branch["Category"]` — cause category
  *   - `  cause["Detail"]` — specific cause (indented)
  */
object IshikawaParser {

  def parse(input: String): IshikawaDb =
    parse(input, new IshikawaDb)

  /** Parses Mermaid Ishikawa (fishbone) source text into the supplied [[IshikawaDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    */
  def parse(input: String, db: IshikawaDb): IshikawaDb = {
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n")

    var i = 0
    while (i < lines.length && !lines(i).trim.toLowerCase.startsWith("ishikawa")) i += 1
    if (i >= lines.length) throw new ParseException("Expected 'ishikawa' keyword", 1, 1)
    i += 1

    while (i < lines.length) {
      val line = lines(i); val trimmed = line.trim; i += 1
      if (trimmed.isEmpty || trimmed.startsWith("%%")) {
        // skip
      } else {
        val indent     = line.length - line.stripLeading().length
        val (_, label) = parseIdLabel(trimmed)

        if (indent < 2) {
          // Check for effect or branch
          if (db.effect.isEmpty && db.branches.isEmpty) {
            db.setEffect(label)
          } else {
            db.addBranch(label)
          }
        } else {
          db.addCauseToLast(label)
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
      val id       = text.substring(0, bracketIdx).trim
      val endIdx   = text.lastIndexOf(']')
      val rawLabel =
        if (endIdx > bracketIdx) text.substring(bracketIdx + 1, endIdx).trim
        else text.substring(bracketIdx + 1).trim
      val label =
        if (rawLabel.startsWith("\"") && rawLabel.endsWith("\""))
          rawLabel.substring(1, rawLabel.length - 1)
        else rawLabel
      (if (id.nonEmpty) id else label, label)
    } else (text, text)
  }
}
