/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/packet/packet.langium
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package packet

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid packet diagram syntax.
  *
  * Supported syntax:
  *   - `packet-beta` — header
  *   - `0-15: "Field Name"` — field with bit range
  *   - `16-31: "Another Field"` — another field
  */
object PacketParser {

  def parse(input: String): PacketDb =
    parse(input, new PacketDb)

  /** Parses Mermaid packet diagram source text into the supplied [[PacketDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param db
    *   the db to parse into
    * @return
    *   the supplied PacketDb, populated
    */
  def parse(input: String, db: PacketDb): PacketDb = {
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("packet-beta")) {
      throw new ParseException("Expected 'packet-beta' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
    parseBody(scanner, db)
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "").replaceAll("%%[^\n]*", "")

  private def parseBody(scanner: Scanner, db: PacketDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%') { skipToNewline(scanner) }
      else if (tryParseTitle(scanner, db)) {}
      else if (tryParseField(scanner, db)) {}
      else { skipToNewline(scanner) }
    }
  }

  private def tryParseTitle(scanner: Scanner, db: PacketDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("title")) { break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()
    db.title = readTextUntilNewline(scanner).trim
    true
  }

  private def tryParseField(scanner: Scanner, db: PacketDb): Boolean = boundary {
    // Format: startBit-endBit: "Label"  or  startBit: "Label"
    val saved = scanner.save()
    if (!scanner.peek().isDigit) { break(false) }

    val startBit = scanner.readNumber().toInt
    val endBit   = if (!scanner.isEof && scanner.peek() == '-') {
      scanner.advance()
      scanner.readNumber().toInt
    } else {
      startBit
    }

    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() != ':') { scanner.restore(saved); break(false) }
    scanner.advance() // consume ':'
    scanner.skipWhitespace()

    val label = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else {
      readTextUntilNewline(scanner).trim
    }

    db.addField(label, startBit, endBit)
    skipToNewline(scanner)
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
