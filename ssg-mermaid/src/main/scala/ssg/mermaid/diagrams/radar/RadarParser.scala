/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the radar chart parser.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package radar

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid radar chart syntax.
  *
  * Supported syntax:
  *   - `radar-beta` — header
  *   - `title "My Chart"` — chart title
  *   - `axis label1, label2, ...` — axis labels
  *   - `"Series Name": v1, v2, ...` — data series
  */
object RadarParser {

  def parse(input: String): RadarDb =
    parse(input, new RadarDb)

  /** Parses Mermaid radar chart source text into the supplied [[RadarDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    */
  def parse(input: String, db: RadarDb): RadarDb = {
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("radar-beta")) {
      throw new ParseException("Expected 'radar-beta' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
    parseBody(scanner, db)
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "").replaceAll("%%[^\n]*", "")

  private def parseBody(scanner: Scanner, db: RadarDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%') { skipToNewline(scanner) }
      else if (tryParseTitle(scanner, db)) {}
      else if (tryParseAxis(scanner, db)) {}
      else if (tryParseSeries(scanner, db)) {}
      else { skipToNewline(scanner) }
    }
  }

  private def tryParseTitle(scanner: Scanner, db: RadarDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("title")) { break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()
    db.title = readTextUntilNewline(scanner).trim
    true
  }

  private def tryParseAxis(scanner: Scanner, db: RadarDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("axis")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && !scanner.peek().isWhitespace) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()

    // Read comma-separated labels
    val line   = readTextUntilNewline(scanner)
    val labels = line.split(",").map(_.trim).filter(_.nonEmpty)
    for (l <- labels) db.addAxis(l)
    true
  }

  private def tryParseSeries(scanner: Scanner, db: RadarDb): Boolean = boundary {
    // "Series Name": v1, v2, ...
    if (scanner.isEof || scanner.peek() != '"') break(false)

    val name = scanner.readQuotedString()
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() != ':') break(false)
    scanner.advance() // consume ':'
    scanner.skipWhitespace()

    val line   = readTextUntilNewline(scanner)
    val values = line.split(",").map(_.trim).filter(_.nonEmpty).flatMap { s =>
      try Some(s.toDouble)
      catch { case _: NumberFormatException => None }
    }
    db.addSeries(name, values.toSeq)
    true
  }

  private def readTextUntilNewline(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n') sb.append(scanner.advance())
    sb.toString
  }

  private def skipToNewline(scanner: Scanner): Unit = {
    while (!scanner.isEof && scanner.peek() != '\n') scanner.advance()
    if (!scanner.isEof) scanner.advance()
  }
}
