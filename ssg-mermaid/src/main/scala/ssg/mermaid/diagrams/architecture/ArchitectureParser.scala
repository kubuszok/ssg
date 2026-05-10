/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the architecture diagram parser.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package architecture

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid architecture diagram syntax.
  *
  * Supported syntax:
  *   - `architecture-beta` — header
  *   - `group id(label)[icon]` — group definition
  *   - `service id(label)[icon] in groupId` — service definition
  *   - `junction id in groupId` — junction definition
  *   - `id:side -- label --> id:side` — edge definition
  */
object ArchitectureParser {

  def parse(input: String): ArchitectureDb = {
    val db      = new ArchitectureDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("architecture-beta")) {
      throw new ParseException("Expected 'architecture-beta' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
    parseBody(scanner, db)
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "").replaceAll("%%[^\n]*", "")

  private def parseBody(scanner: Scanner, db: ArchitectureDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%') { skipToNewline(scanner) }
      else if (tryParseGroup(scanner, db)) {}
      else if (tryParseService(scanner, db)) {}
      else if (tryParseJunction(scanner, db)) {}
      else if (tryParseEdge(scanner, db)) {}
      else { skipToNewline(scanner) }
    }
  }

  private def tryParseGroup(scanner: Scanner, db: ArchitectureDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("group")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && !scanner.peek().isWhitespace) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()
    val id = readIdent(scanner)
    scanner.skipWhitespace()
    val label = if (!scanner.isEof && scanner.peek() == '(') {
      scanner.advance(); val l = scanner.readUntil(')'); l.trim
    } else id
    db.addGroup(id, label)
    skipToNewline(scanner)
    true
  }

  private def tryParseService(scanner: Scanner, db: ArchitectureDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("service")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && !scanner.peek().isWhitespace) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()
    val id = readIdent(scanner)
    scanner.skipWhitespace()
    val label = if (!scanner.isEof && scanner.peek() == '(') {
      scanner.advance(); scanner.readUntil(')').trim
    } else id
    scanner.skipWhitespace()
    val icon = if (!scanner.isEof && scanner.peek() == '[') {
      scanner.advance(); scanner.readUntil(']').trim
    } else ""
    scanner.skipWhitespace()
    val group = if (!scanner.isEof && scanner.matchStrIgnoreCase("in")) {
      scanner.skipWhitespace(); readIdent(scanner)
    } else ""
    db.addService(id, label, icon, group)
    skipToNewline(scanner)
    true
  }

  private def tryParseJunction(scanner: Scanner, db: ArchitectureDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("junction")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && !scanner.peek().isWhitespace) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()
    val id = readIdent(scanner)
    scanner.skipWhitespace()
    val group = if (!scanner.isEof && scanner.matchStrIgnoreCase("in")) {
      scanner.skipWhitespace(); readIdent(scanner)
    } else ""
    db.addJunction(id, group)
    skipToNewline(scanner)
    true
  }

  private def tryParseEdge(scanner: Scanner, db: ArchitectureDb): Boolean = boundary {
    // Format: id:side -- label --> id:side  or  id:side --> id:side
    val saved    = scanner.save()
    val fromPart = readUntilArrowOrNewline(scanner)
    if (fromPart.isEmpty || scanner.isEof || scanner.peek() == '\n') {
      scanner.restore(saved); break(false)
    }

    val (fromId, fromSide) = splitIdSide(fromPart.trim)
    scanner.skipWhitespace()

    // Read edge: -- label --> or -->
    var edgeLabel = ""
    if (!scanner.isEof && scanner.peek() == '-' && scanner.peekAt(1) == '-') {
      scanner.advance(); scanner.advance()
      if (!scanner.isEof && scanner.peek() == '>') {
        scanner.advance() // simple -->
      } else {
        // -- label -->
        scanner.skipWhitespace()
        val lb = new StringBuilder()
        while (!scanner.isEof && !(scanner.peek() == '-' && scanner.peekAt(1) == '-'))
          lb.append(scanner.advance())
        edgeLabel = lb.toString.trim
        if (!scanner.isEof) { scanner.advance(); scanner.advance() } // consume --
        if (!scanner.isEof && scanner.peek() == '>') scanner.advance()
      }
    } else {
      scanner.restore(saved); break(false)
    }

    scanner.skipWhitespace()
    val toPart         = readTextUntilNewline(scanner).trim
    val (toId, toSide) = splitIdSide(toPart)
    if (fromId.nonEmpty && toId.nonEmpty) {
      db.addEdge(fromId, toId, fromSide, toSide, edgeLabel)
    }
    true
  }

  private def splitIdSide(s: String): (String, String) = {
    val idx = s.indexOf(':')
    if (idx >= 0) (s.substring(0, idx).trim, s.substring(idx + 1).trim)
    else (s.trim, "")
  }

  private def readIdent(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && (scanner.peek().isLetterOrDigit || scanner.peek() == '_' || scanner.peek() == '-'))
      sb.append(scanner.advance())
    sb.toString
  }

  private def readUntilArrowOrNewline(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (
      !scanner.isEof && scanner.peek() != '\n' &&
      !(scanner.peek() == '-' && scanner.peekAt(1) == '-')
    )
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
