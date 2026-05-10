/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid venn diagram concept
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package venn

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid Venn diagram syntax.
  *
  * Supported syntax:
  *   - `venn-beta` — header
  *   - `title "My Diagram"` — title
  *   - `set A["Label"]` — set definition
  *   - `intersection A,B["Label"]` — intersection label
  */
object VennParser {

  def parse(input: String): VennDb = {
    val db      = new VennDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("venn-beta")) {
      throw new ParseException("Expected 'venn-beta' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
    parseBody(scanner, db)
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "").replaceAll("%%[^\n]*", "")

  private def parseBody(scanner: Scanner, db: VennDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%') { skipToNewline(scanner) }
      else if (tryParseTitle(scanner, db)) {}
      else if (tryParseSet(scanner, db)) {}
      else if (tryParseIntersection(scanner, db)) {}
      else { skipToNewline(scanner) }
    }
  }

  private def tryParseTitle(scanner: Scanner, db: VennDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("title")) { break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()
    db.title = readTextUntilNewline(scanner).trim
    true
  }

  private def tryParseSet(scanner: Scanner, db: VennDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("set")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && !scanner.peek().isWhitespace) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()

    val id = readIdent(scanner)
    if (id.isEmpty) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()

    val label = if (!scanner.isEof && scanner.peek() == '[') {
      scanner.advance()
      if (!scanner.isEof && scanner.peek() == '"') {
        val l = scanner.readQuotedString()
        if (!scanner.isEof && scanner.peek() == ']') scanner.advance()
        l
      } else {
        scanner.readUntil(']').trim
      }
    } else id

    db.addSet(id, label)
    skipToNewline(scanner)
    true
  }

  private def tryParseIntersection(scanner: Scanner, db: VennDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("intersection")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && !scanner.peek().isWhitespace) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()

    // Read comma-separated set IDs until [
    val setIds = scala.collection.mutable.ArrayBuffer.empty[String]
    while (!scanner.isEof && scanner.peek() != '[' && scanner.peek() != '\n') {
      val id = readIdent(scanner)
      if (id.nonEmpty) setIds += id
      scanner.skipWhitespace()
      if (!scanner.isEof && scanner.peek() == ',') { scanner.advance(); scanner.skipWhitespace() }
    }

    val label = if (!scanner.isEof && scanner.peek() == '[') {
      scanner.advance()
      if (!scanner.isEof && scanner.peek() == '"') {
        val l = scanner.readQuotedString()
        if (!scanner.isEof && scanner.peek() == ']') scanner.advance()
        l
      } else {
        scanner.readUntil(']').trim
      }
    } else ""

    if (setIds.size >= 2) {
      db.addIntersection(setIds.toSeq, label)
    }
    skipToNewline(scanner)
    true
  }

  private def readIdent(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && (scanner.peek().isLetterOrDigit || scanner.peek() == '_'))
      sb.append(scanner.advance())
    sb.toString
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
