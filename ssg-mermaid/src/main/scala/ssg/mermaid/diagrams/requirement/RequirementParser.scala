/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/requirement/requirementDiagram.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing; RequirementDb for accumulation
 *   Renames: JISON grammar -> RequirementParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package requirement

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid requirement diagram syntax.
  *
  * Supported syntax:
  *   - `requirementDiagram` — header
  *   - `requirement name {` ... `}` — requirement block
  *   - `element name {` ... `}` — element block
  *   - `name - relType -> name` — relationship
  */
object RequirementParser {

  private val ReqTypes: Set[String] = Set(
    "requirement",
    "functionalrequirement",
    "interfacerequirement",
    "performancerequirement",
    "physicalrequirement",
    "designconstraint"
  )

  /** Parses Mermaid requirement diagram source text into a [[RequirementDb]]. */
  def parse(input: String): RequirementDb = {
    val db      = new RequirementDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("requirementDiagram")) {
      throw new ParseException("Expected 'requirementDiagram' keyword", scanner.line, scanner.col)
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

  private def parseBody(scanner: Scanner, db: RequirementDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (tryParseAccTitle(scanner, db)) {
        // parsed
      } else if (tryParseAccDescr(scanner, db)) {
        // parsed
      } else if (tryParseRequirementBlock(scanner, db)) {
        // parsed
      } else if (tryParseElementBlock(scanner, db)) {
        // parsed
      } else if (tryParseRelationship(scanner, db)) {
        // parsed
      } else {
        skipToNewline(scanner)
      }
    }
  }

  private def tryParseRequirementBlock(scanner: Scanner, db: RequirementDb): Boolean = boundary {
    val saved = scanner.save()
    // Try to read a requirement type keyword
    val keyword = readIdentifier(scanner).toLowerCase
    if (!ReqTypes.contains(keyword)) {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()

    // Read the requirement name (may be quoted or unquoted)
    val name = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else {
      readTextUntilChar(scanner, '{').trim
    }
    if (name.isEmpty) { scanner.restore(saved); break(false) }

    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() != '{') { scanner.restore(saved); break(false) }
    scanner.advance() // consume '{'

    db.addRequirement(name, keyword)

    // Parse properties inside the block
    parseBlockProperties(scanner) { (prop, value) =>
      db.setRequirementProperty(name, prop, value)
    }
    true
  }

  private def tryParseElementBlock(scanner: Scanner, db: RequirementDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("element")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()

    val name = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else {
      readTextUntilChar(scanner, '{').trim
    }
    if (name.isEmpty) { scanner.restore(saved); break(false) }

    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() != '{') { scanner.restore(saved); break(false) }
    scanner.advance()

    db.addElement(name)

    parseBlockProperties(scanner) { (prop, value) =>
      db.setElementProperty(name, prop, value)
    }
    true
  }

  private def parseBlockProperties(scanner: Scanner)(handler: (String, String) => Unit): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()
      if (scanner.peek() == '}') { scanner.advance(); break() }

      // Read property: value
      val prop = readIdentifier(scanner).trim
      scanner.skipWhitespace()
      if (!scanner.isEof && scanner.peek() == ':') {
        scanner.advance()
        scanner.skipWhitespace()
        val value = readTextUntilNewline(scanner).trim
        if (prop.nonEmpty) handler(prop, value)
      } else {
        skipToNewline(scanner)
      }
    }
  }

  private def tryParseRelationship(scanner: Scanner, db: RequirementDb): Boolean = boundary {
    // Format: src - relType -> dst
    val saved = scanner.save()
    val src   = readTextUntilChar(scanner, '-').trim
    if (src.isEmpty || scanner.isEof) { scanner.restore(saved); break(false) }

    if (scanner.peek() != '-') { scanner.restore(saved); break(false) }
    scanner.advance() // consume first '-'
    scanner.skipWhitespace()

    val relType = readIdentifier(scanner).trim
    if (relType.isEmpty) { scanner.restore(saved); break(false) }

    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() != '-') { scanner.restore(saved); break(false) }
    scanner.advance() // consume '-'
    if (scanner.isEof || scanner.peek() != '>') { scanner.restore(saved); break(false) }
    scanner.advance() // consume '>'
    scanner.skipWhitespace()

    val dst = readTextUntilNewline(scanner).trim
    if (dst.isEmpty) { scanner.restore(saved); break(false) }

    db.addRelationship(src, dst, relType)
    true
  }

  private def tryParseAccTitle(scanner: Scanner, db: RequirementDb): Boolean = boundary {
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

  private def tryParseAccDescr(scanner: Scanner, db: RequirementDb): Boolean = boundary {
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

  private def readTextUntilChar(scanner: Scanner, c: Char): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != c && scanner.peek() != '\n') sb.append(scanner.advance())
    sb.toString
  }

  private def skipToNewline(scanner: Scanner): Unit = {
    while (!scanner.isEof && scanner.peek() != '\n') scanner.advance()
    if (!scanner.isEof) scanner.advance()
  }
}
