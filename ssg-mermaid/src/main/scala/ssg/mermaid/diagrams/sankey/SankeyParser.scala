/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sankey/sankey.langium
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces Langium-generated parser with hand-written parser
 *   Idiom: CSV-style line parsing; SankeyDb for accumulation
 *   Renames: Langium grammar -> SankeyParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sankey

import ssg.mermaid.parse.ParseException

/** Hand-written parser for Mermaid Sankey diagram syntax.
  *
  * The Sankey syntax uses a CSV-like format:
  *   - `sankey-beta` — header
  *   - Blank lines separate sections
  *   - Each data line: `source,target,value`
  *   - Values in quotes are supported: `"Source Name","Target Name",100`
  */
object SankeyParser {

  /** Parses Mermaid Sankey diagram source text into a [[SankeyDb]]. */
  def parse(input: String): SankeyDb =
    parse(input, new SankeyDb)

  /** Parses Mermaid Sankey diagram source text into the supplied [[SankeyDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    */
  def parse(input: String, db: SankeyDb): SankeyDb = {
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n").map(_.trim)

    var i = 0
    // Skip to header
    while (i < lines.length && !lines(i).toLowerCase.startsWith("sankey-beta"))
      i += 1
    if (i >= lines.length) {
      throw new ParseException("Expected 'sankey-beta' keyword", 1, 1)
    }
    i += 1 // skip header line

    // Parse CSV-like flow lines
    while (i < lines.length) {
      val line = lines(i).trim
      i += 1

      if (line.isEmpty || line.startsWith("%%")) {
        // skip blank lines and comments
      } else {
        // Parse CSV: source, target, value
        val parts = parseCsvLine(line)
        if (parts.length >= 3) {
          val source = parts(0).trim
          val target = parts(1).trim
          val value  =
            try
              parts(2).trim.toDouble
            catch {
              case _: NumberFormatException => 0.0
            }
          if (source.nonEmpty && target.nonEmpty) {
            db.addFlow(source, target, value)
          }
        }
      }
    }

    db
  }

  private def cleanInput(input: String): String = {
    var s = input
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    s
  }

  /** Parses a CSV line, supporting quoted values. */
  private def parseCsvLine(line: String): Array[String] = {
    val result  = scala.collection.mutable.ArrayBuffer.empty[String]
    val sb      = new StringBuilder()
    var inQuote = false
    var i       = 0
    while (i < line.length) {
      val c = line.charAt(i)
      if (inQuote) {
        if (c == '"') {
          inQuote = false
        } else {
          sb.append(c)
        }
      } else {
        if (c == '"') {
          inQuote = true
        } else if (c == ',') {
          result += sb.toString
          sb.clear()
        } else {
          sb.append(c)
        }
      }
      i += 1
    }
    result += sb.toString
    result.toArray
  }
}
