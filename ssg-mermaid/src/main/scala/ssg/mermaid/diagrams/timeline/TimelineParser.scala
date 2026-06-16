/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/timeline/timelineDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces Langium parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; TimelineDb for accumulation
 *   Renames: timeline parser -> TimelineParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package timeline

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid timeline syntax.
  *
  * Supported syntax:
  *   - `timeline` — diagram header
  *   - `title My Timeline` — optional title
  *   - `section Period Name` — section grouping
  *   - `Period : event1 : event2` — period with colon-separated events
  */
object TimelineParser {

  /** Parses Mermaid timeline source text into a [[TimelineDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated TimelineDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): TimelineDb =
    parse(input, new TimelineDb)

  /** Parses Mermaid timeline source text into the supplied [[TimelineDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param db
    *   the db to parse into
    * @return
    *   the supplied TimelineDb, populated
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String, db: TimelineDb): TimelineDb = {
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    parseHeader(scanner)
    parseBody(scanner, db)

    db
  }

  /** Removes directives and comments from input. */
  private def cleanInput(input: String): String = {
    var s = input
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    s = s.replaceAll("%%[^\n]*", "")
    s
  }

  /** Parses the timeline keyword. */
  private def parseHeader(scanner: Scanner): Unit = {
    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("timeline")) {
      throw new ParseException("Expected 'timeline' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
  }

  /** Parses the body: title, sections, and period/event entries. */
  private def parseBody(scanner: Scanner, db: TimelineDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else {
        parseLine(scanner, db)
      }
    }
  }

  /** Parses a single line. */
  private def parseLine(scanner: Scanner, db: TimelineDb): Unit = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') break()

    if (tryParseTitle(scanner, db)) break()
    if (tryParseSection(scanner, db)) break()
    if (tryParseAccTitle(scanner, db)) break()
    if (tryParseAccDescr(scanner, db)) break()

    // Default: period : event1 : event2
    parsePeriodLine(scanner, db)
  }

  /** Tries to parse `title ...`. */
  private def tryParseTitle(scanner: Scanner, db: TimelineDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("title")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.title = readTextUntilNewline(scanner).trim
    true
  }

  /** Tries to parse `section ...`. */
  private def tryParseSection(scanner: Scanner, db: TimelineDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("section")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.addSection(readTextUntilNewline(scanner).trim)
    true
  }

  /** Tries to parse accTitle. */
  private def tryParseAccTitle(scanner: Scanner, db: TimelineDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accTitle")) break(false)
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      db.accTitle = readTextUntilNewline(scanner).trim
      break(true)
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse accDescr. */
  private def tryParseAccDescr(scanner: Scanner, db: TimelineDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accDescr")) break(false)
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      db.accDescription = readTextUntilNewline(scanner).trim
      break(true)
    }
    scanner.restore(saved)
    false
  }

  /** Parses a period line: `Period title : event1 : event2`. */
  private def parsePeriodLine(scanner: Scanner, db: TimelineDb): Unit = {
    val lineText = readTextUntilNewline(scanner).trim
    if (lineText.nonEmpty) {
      // Split on colons
      val parts = lineText.split(":").map(_.trim).filter(_.nonEmpty)

      if (parts.length >= 1) {
        val periodTitle = parts(0)
        val events      = if (parts.length > 1) parts.drop(1) else Array.empty[String]
        db.addPeriod(periodTitle, events)
      }
    }
  }

  /** Reads text until newline or EOF. */
  private def readTextUntilNewline(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n')
      sb.append(scanner.advance())
    sb.toString
  }

  /** Skips to the next newline or EOF. */
  private def skipToNewline(scanner: Scanner): Unit = {
    while (!scanner.isEof && scanner.peek() != '\n')
      scanner.advance()
    if (!scanner.isEof) scanner.advance()
  }
}
