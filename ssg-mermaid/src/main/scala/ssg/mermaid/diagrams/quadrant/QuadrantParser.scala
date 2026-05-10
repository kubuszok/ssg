/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/quadrant-chart/quadrantChart.langium
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces Langium-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing; QuadrantDb for accumulation
 *   Renames: Langium grammar -> QuadrantParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package quadrant

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid quadrant chart syntax.
  *
  * Supported syntax:
  *   - `quadrantChart` — header
  *   - `title "My Chart"` — chart title
  *   - `x-axis "Left" --> "Right"` — x-axis labels
  *   - `y-axis "Bottom" --> "Top"` — y-axis labels
  *   - `quadrant-1 "Label"` — quadrant labels (1-4)
  *   - `Point Name: [x, y]` — data points
  */
object QuadrantParser {

  /** Parses Mermaid quadrant chart source text into a [[QuadrantDb]]. */
  def parse(input: String): QuadrantDb = {
    val db      = new QuadrantDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("quadrantChart")) {
      throw new ParseException("Expected 'quadrantChart' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)

    parseBody(scanner, db)
    db
  }

  private def cleanInput(input: String): String = {
    var s = input
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    s = s.replaceAll("%%[^\n]*", "")
    s
  }

  private def parseBody(scanner: Scanner, db: QuadrantDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (tryParseTitle(scanner, db)) {
        // parsed
      } else if (tryParseXAxis(scanner, db)) {
        // parsed
      } else if (tryParseYAxis(scanner, db)) {
        // parsed
      } else if (tryParseQuadrantLabel(scanner, db)) {
        // parsed
      } else if (tryParseAccTitle(scanner, db)) {
        // parsed
      } else if (tryParseAccDescr(scanner, db)) {
        // parsed
      } else if (tryParsePoint(scanner, db)) {
        // parsed
      } else {
        skipToNewline(scanner)
      }
    }
  }

  private def tryParseTitle(scanner: Scanner, db: QuadrantDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("title")) { break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()
    db.title = readTextUntilNewline(scanner).trim
    true
  }

  private def tryParseXAxis(scanner: Scanner, db: QuadrantDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("x-axis")) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()

    // Left label
    val left =
      if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString()
      else readWordUntilArrow(scanner).trim
    db.xAxisLeftLabel = left
    scanner.skipWhitespace()

    // Optional --> Right label
    if (!scanner.isEof && scanner.matchStr("-->")) {
      scanner.skipWhitespace()
      val right =
        if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString()
        else readTextUntilNewline(scanner).trim
      db.xAxisRightLabel = right
    }
    skipToNewline(scanner)
    true
  }

  private def tryParseYAxis(scanner: Scanner, db: QuadrantDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("y-axis")) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()

    val bottom =
      if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString()
      else readWordUntilArrow(scanner).trim
    db.yAxisBottomLabel = bottom
    scanner.skipWhitespace()

    if (!scanner.isEof && scanner.matchStr("-->")) {
      scanner.skipWhitespace()
      val top =
        if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString()
        else readTextUntilNewline(scanner).trim
      db.yAxisTopLabel = top
    }
    skipToNewline(scanner)
    true
  }

  private def tryParseQuadrantLabel(scanner: Scanner, db: QuadrantDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("quadrant-")) { scanner.restore(saved); break(false) }
    if (scanner.isEof || !scanner.peek().isDigit) { scanner.restore(saved); break(false) }
    val digit = scanner.advance()
    val index = digit - '0'
    scanner.skipWhitespace()
    val label = readTextUntilNewline(scanner).trim
    db.setQuadrantLabel(index, label)
    true
  }

  private def tryParseAccTitle(scanner: Scanner, db: QuadrantDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accTitle")) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance(); scanner.skipWhitespace()
      db.accTitle = readTextUntilNewline(scanner).trim
      break(true)
    }
    scanner.restore(saved)
    false
  }

  private def tryParseAccDescr(scanner: Scanner, db: QuadrantDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accDescr")) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance(); scanner.skipWhitespace()
      db.accDescription = readTextUntilNewline(scanner).trim
      break(true)
    }
    scanner.restore(saved)
    false
  }

  private def tryParsePoint(scanner: Scanner, db: QuadrantDb): Boolean = boundary {
    // Point format: "Label Name": [x, y]
    val saved = scanner.save()
    // Read label text until colon
    val labelPart = readTextUntilChar(scanner, ':')
    if (labelPart.trim.isEmpty) { scanner.restore(saved); break(false) }

    if (scanner.isEof || scanner.peek() != ':') { scanner.restore(saved); break(false) }
    scanner.advance() // consume ':'
    scanner.skipWhitespace()

    if (scanner.isEof || scanner.peek() != '[') { scanner.restore(saved); break(false) }
    scanner.advance() // consume '['
    scanner.skipWhitespace()

    // Read x
    if (scanner.isEof || (!scanner.peek().isDigit && scanner.peek() != '-' && scanner.peek() != '.')) {
      scanner.restore(saved); break(false)
    }
    val x = scanner.readNumber()
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ',') scanner.advance()
    scanner.skipWhitespace()

    // Read y
    if (scanner.isEof || (!scanner.peek().isDigit && scanner.peek() != '-' && scanner.peek() != '.')) {
      scanner.restore(saved); break(false)
    }
    val y = scanner.readNumber()
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ']') scanner.advance()

    db.addPoint(labelPart.trim, x, y)
    skipToNewline(scanner)
    true
  }

  private def readTextUntilNewline(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n') sb.append(scanner.advance())
    sb.toString
  }

  private def readTextUntilChar(scanner: Scanner, c: Char): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != c && scanner.peek() != '\n') sb.append(scanner.advance())
    sb.toString
  }

  private def readWordUntilArrow(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n' && !(scanner.peek() == '-' && scanner.peekAt(1) == '-'))
      sb.append(scanner.advance())
    sb.toString
  }

  private def skipToNewline(scanner: Scanner): Unit = {
    while (!scanner.isEof && scanner.peek() != '\n') scanner.advance()
    if (!scanner.isEof) scanner.advance()
  }
}
