/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/block/block.langium
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces Langium-generated parser with hand-written parser
 *   Idiom: Scanner-based parsing; BlockDb for accumulation
 *   Renames: Langium grammar -> BlockParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package block

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid block diagram syntax.
  *
  * Supported syntax:
  *   - `block-beta` — header
  *   - `columns N` — set column count
  *   - `id` or `id["Label"]` — block definition
  *   - `id:N` — block spanning N columns
  *   - `id --> id2` — edge
  *   - Empty lines start new rows
  */
object BlockParser {

  /** Parses Mermaid block diagram source text into a [[BlockDb]]. */
  def parse(input: String): BlockDb =
    parse(input, new BlockDb)

  /** Parses Mermaid block diagram source text into the supplied [[BlockDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param db
    *   the db to parse into
    * @return
    *   the supplied BlockDb, populated
    */
  def parse(input: String, db: BlockDb): BlockDb = {
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("block-beta")) {
      throw new ParseException("Expected 'block-beta' keyword", scanner.line, scanner.col)
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

  private def parseBody(scanner: Scanner, db: BlockDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespace()
      if (scanner.isEof) break()

      // Check for empty line (new row)
      if (scanner.peek() == '\n') {
        scanner.advance()
        // Check if next is also newline (double newline = new row)
        if (!scanner.isEof && scanner.peek() == '\n') {
          scanner.advance()
          db.newRow()
        }
      } else if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (tryParseColumns(scanner, db)) {
        // parsed
      } else if (tryParseAccTitle(scanner, db)) {
        // parsed
      } else if (tryParseAccDescr(scanner, db)) {
        // parsed
      } else {
        // Try to parse block or edge
        parseBlockOrEdge(scanner, db)
      }
    }
  }

  private def tryParseColumns(scanner: Scanner, db: BlockDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("columns")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek().isDigit) {
      db.columns = scanner.readNumber().toInt
    }
    skipToNewline(scanner)
    true
  }

  private def tryParseAccTitle(scanner: Scanner, db: BlockDb): Boolean = boundary {
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

  private def tryParseAccDescr(scanner: Scanner, db: BlockDb): Boolean = boundary {
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

  private def parseBlockOrEdge(scanner: Scanner, db: BlockDb): Unit = {
    // Read first identifier
    val id = readIdentifier(scanner)
    if (id.isEmpty) { skipToNewline(scanner) }
    else {

      scanner.skipWhitespace()

      // Check for label in brackets: id["Label"]
      val label = if (!scanner.isEof && scanner.peek() == '[') {
        scanner.advance()
        if (!scanner.isEof && scanner.peek() == '"') {
          val lbl = scanner.readQuotedString()
          scanner.skipWhitespace()
          if (!scanner.isEof && scanner.peek() == ']') scanner.advance()
          lbl
        } else {
          val lbl = scanner.readUntil(']')
          lbl.trim
        }
      } else if (!scanner.isEof && scanner.peek() == '(') {
        scanner.advance()
        if (!scanner.isEof && scanner.peek() == '"') {
          val lbl = scanner.readQuotedString()
          scanner.skipWhitespace()
          if (!scanner.isEof && scanner.peek() == ')') scanner.advance()
          lbl
        } else {
          val lbl = scanner.readUntil(')')
          lbl.trim
        }
      } else {
        id
      }

      scanner.skipWhitespace()

      // Check for width: id:N
      var width = 1
      if (!scanner.isEof && scanner.peek() == ':') {
        scanner.advance()
        if (!scanner.isEof && scanner.peek().isDigit) {
          width = scanner.readNumber().toInt
        }
      }

      scanner.skipWhitespace()

      // Check for edge: --> id2 or -- "label" --> id2
      if (!scanner.isEof && scanner.peek() == '-' && scanner.peekAt(1) == '-') {
        db.addBlock(id, label, width)
        // Handle: -- "label" --> or -->
        scanner.advance(); scanner.advance() // consume --
        scanner.skipWhitespace()
        var edgeLabel = ""
        if (!scanner.isEof && scanner.peek() == '"') {
          edgeLabel = scanner.readQuotedString()
          scanner.skipWhitespace()
          // Expect -->
          if (!scanner.isEof && scanner.peek() == '-' && scanner.peekAt(1) == '-' && scanner.peekAt(2) == '>') {
            scanner.advance(); scanner.advance(); scanner.advance()
          }
        } else if (!scanner.isEof && scanner.peek() == '>') {
          scanner.advance() // consume >
        }
        scanner.skipWhitespace()

        // Parse target node (may have label)
        val targetId = readIdentifier(scanner)
        if (targetId.nonEmpty) {
          scanner.skipWhitespace()
          val targetLabel = if (!scanner.isEof && scanner.peek() == '[') {
            scanner.advance()
            if (!scanner.isEof && scanner.peek() == '"') {
              val lbl = scanner.readQuotedString()
              scanner.skipWhitespace()
              if (!scanner.isEof && scanner.peek() == ']') scanner.advance()
              lbl
            } else {
              scanner.readUntil(']').trim
            }
          } else {
            targetId
          }
          db.addBlock(targetId, targetLabel)
          db.addEdge(id, targetId, edgeLabel)
        }
      } else {
        db.addBlock(id, label, width)
      }

      // Do not skipToNewline — there may be more blocks on the same line
    }
  }

  private def readIdentifier(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && (scanner.peek().isLetterOrDigit || scanner.peek() == '_' || scanner.peek() == '-'))
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
