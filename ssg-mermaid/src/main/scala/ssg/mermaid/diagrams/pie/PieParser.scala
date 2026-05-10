/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/pie/pie.langium (grammar)
 *              mermaid/packages/mermaid/src/diagrams/pie/pieDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces Langium-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; PieDb for accumulation
 *   Renames: Langium grammar -> PieParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package pie

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid pie chart syntax.
  *
  * Parses the grammar defined in the Langium grammar, producing a populated [[PieDb]].
  *
  * Supported syntax:
  *   - `pie` — chart header
  *   - `showData` — optional flag to show values
  *   - `title My Title` — optional chart title
  *   - `"Label" : value` — section definition (quoted label, colon separator, numeric value)
  *   - `accTitle: text` — accessibility title
  *   - `accDescr: text` — accessibility description
  */
object PieParser {

  /** Parses Mermaid pie chart source text into a [[PieDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated PieDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): PieDb = {
    val db      = new PieDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    // Skip leading whitespace/newlines
    scanner.skipWhitespaceAndNewlines()

    // Parse pie keyword
    parsePieHeader(scanner, db)

    // Parse sections and optional directives
    parseBody(scanner, db)

    db
  }

  /** Removes directives and comments from input. */
  private def cleanInput(input: String): String = {
    var s = input
    // Remove %%{...}%% directives
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    // Remove %% comments (to end of line)
    s = s.replaceAll("%%[^\n]*", "")
    s
  }

  /** Parses the pie header keyword. Also checks for `showData`. */
  private def parsePieHeader(scanner: Scanner, db: PieDb): Unit = {
    scanner.skipWhitespaceAndNewlines()

    if (!scanner.matchStrIgnoreCase("pie")) {
      throw new ParseException("Expected 'pie' keyword", scanner.line, scanner.col)
    }

    // Check for showData or title on the same line
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() != '\n') {
      if (scanner.matchStrIgnoreCase("showData")) {
        db.showData = true
        scanner.skipWhitespace()
      }
      // Check for title on the same line: pie title My Title
      if (!scanner.isEof && scanner.matchStrIgnoreCase("title")) {
        scanner.skipWhitespace()
        db.title = readTextUntilNewline(scanner).trim
      }
    }

    skipToNewline(scanner)
  }

  /** Parses the body: title, accTitle, accDescr, and section definitions. */
  private def parseBody(scanner: Scanner, db: PieDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      // Skip comments
      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (scanner.peek() == ';') {
        scanner.advance()
      } else if (tryParseTitle(scanner, db)) {
        // title parsed
      } else if (tryParseAccTitle(scanner, db)) {
        // accTitle parsed
      } else if (tryParseAccDescr(scanner, db)) {
        // accDescr parsed
      } else if (scanner.peek() == '"') {
        // Section definition: "Label" : value
        parseSection(scanner, db)
      } else {
        // Unknown token — skip to next line
        skipToNewline(scanner)
      }
    }
  }

  /** Tries to parse a title line. Returns true if matched. */
  private def tryParseTitle(scanner: Scanner, db: PieDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("title")) {
      break(false)
    }
    // Must be followed by whitespace
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.title = readTextUntilNewline(scanner).trim
    true
  }

  /** Tries to parse accTitle. Returns true if matched. */
  private def tryParseAccTitle(scanner: Scanner, db: PieDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accTitle")) {
      break(false)
    }
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

  /** Tries to parse accDescr. Returns true if matched. */
  private def tryParseAccDescr(scanner: Scanner, db: PieDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accDescr")) {
      break(false)
    }
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      db.accDescription = readTextUntilNewline(scanner).trim
      break(true)
    } else if (!scanner.isEof && scanner.peek() == '{') {
      scanner.advance()
      val content = scanner.readUntil('}')
      db.accDescription = content.trim
      break(true)
    }
    scanner.restore(saved)
    false
  }

  /** Parses a section: `"Label" : value`. */
  private def parseSection(scanner: Scanner, db: PieDb): Unit = {
    // Read quoted label
    val label = scanner.readQuotedString()

    scanner.skipWhitespace()

    // Expect colon
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
    }

    scanner.skipWhitespace()

    // Read numeric value
    val value = if (!scanner.isEof && (scanner.peek().isDigit || scanner.peek() == '-' || scanner.peek() == '.')) {
      scanner.readNumber()
    } else {
      0.0
    }

    db.addSection(label, value)

    skipToNewline(scanner)
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
    if (!scanner.isEof) scanner.advance() // consume newline
  }
}
