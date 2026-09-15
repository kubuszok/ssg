/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/er/parser/erDiagram.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; ErDb for accumulation
 *   Renames: erDiagram.jison -> ErParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package er

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid ER diagram syntax.
  *
  * Parses the grammar defined in `erDiagram.jison`, producing a populated [[ErDb]].
  *
  * Supported syntax:
  *   - `erDiagram` — diagram header
  *   - `CUSTOMER ||--o{ ORDER : places` — relationship with cardinality markers
  *   - `CUSTOMER { string name }` — entity with attributes
  *   - `title`, `accTitle`, `accDescr` — metadata
  */
object ErParser {

  /** Parses Mermaid ER diagram source text into an [[ErDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated ErDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): ErDb =
    parse(input, new ErDb)

  /** Parses Mermaid ER diagram source text into the supplied [[ErDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param db
    *   the db to parse into
    * @return
    *   the supplied ErDb, populated
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String, db: ErDb): ErDb = {
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()

    // Parse header
    parseHeader(scanner)

    // Parse body
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

  /** Parses the erDiagram keyword. */
  private def parseHeader(scanner: Scanner): Unit = {
    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("erDiagram")) {
      throw new ParseException("Expected 'erDiagram' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
  }

  /** Parses the body: relationships, entities with attributes, and metadata. */
  private def parseBody(scanner: Scanner, db: ErDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      // Skip comments
      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (tryParseTitle(scanner, db)) {
        // title parsed
      } else if (tryParseAccTitle(scanner, db)) {
        // accTitle parsed
      } else if (tryParseAccDescr(scanner, db)) {
        // accDescr parsed
      } else {
        // Try to parse entity or relationship
        parseEntityOrRelationship(scanner, db)
      }
    }
  }

  /** Parses either an entity definition (with braces) or a relationship statement. */
  private def parseEntityOrRelationship(scanner: Scanner, db: ErDb): Unit = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') break()

    // Read first entity name
    val entityA = readIdentifier(scanner)
    if (entityA.isEmpty) {
      skipToNewline(scanner)
      break()
    }

    scanner.skipWhitespace()

    if (scanner.isEof || scanner.peek() == '\n') {
      // Just an entity declaration
      db.addEntity(entityA)
      break()
    }

    // Check if it's an entity with attributes: ENTITY { ... }
    if (scanner.peek() == '{') {
      scanner.advance() // consume {
      parseEntityAttributes(scanner, db, entityA)
      break()
    }

    // Otherwise, try to parse a relationship: ENTITY_A ||--o{ ENTITY_B : label
    val saved     = scanner.save()
    val relResult = tryParseRelationship(scanner)
    relResult match {
      case Some((roleA, identification, roleB)) =>
        scanner.skipWhitespace()
        val entityB = readIdentifier(scanner)
        if (entityB.isEmpty) {
          scanner.restore(saved)
          skipToNewline(scanner)
          break()
        }

        scanner.skipWhitespace()
        // Expect colon and label
        var label = ""
        if (!scanner.isEof && scanner.peek() == ':') {
          scanner.advance()
          scanner.skipWhitespace()
          label = readTextUntilNewline(scanner).trim
          // Strip surrounding quotes if present
          if (label.startsWith("\"") && label.endsWith("\"")) {
            label = label.substring(1, label.length - 1)
          }
        }

        db.addRelationship(entityA, roleA, entityB, roleB, identification, label)

      case None =>
        // Not a relationship — just an entity
        scanner.restore(saved)
        db.addEntity(entityA)
        skipToNewline(scanner)
    }
  }

  /** Parses entity attributes inside braces.
    *
    * Grammar: `{ type name ["PK"|"FK"|"UK"] ["comment"] }`
    */
  private def parseEntityAttributes(scanner: Scanner, db: ErDb, entityName: String): Unit = boundary {
    db.addEntity(entityName)

    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '}') {
        scanner.advance()
        break()
      }

      // Read attribute type
      val attrType = readIdentifier(scanner)
      if (attrType.isEmpty) {
        skipToNewline(scanner)
      } else {
        scanner.skipWhitespace()
        // Read attribute name
        val attrName = readIdentifier(scanner)
        scanner.skipWhitespace()

        // Optional key type: PK, FK, UK
        var keyType = ""
        if (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != '}') {
          val saved        = scanner.save()
          val potentialKey = readIdentifier(scanner)
          if (potentialKey == "PK" || potentialKey == "FK" || potentialKey == "UK") {
            keyType = potentialKey
          } else {
            scanner.restore(saved)
          }
        }

        scanner.skipWhitespace()

        // Optional comment (quoted string)
        var comment = ""
        if (!scanner.isEof && scanner.peek() == '"') {
          comment = scanner.readQuotedString()
        }

        db.addAttribute(
          entityName,
          ErAttribute(
            attributeType = attrType,
            attributeName = attrName,
            attributeKeyType = keyType,
            attributeComment = comment
          )
        )
      }
    }
  }

  /** Tries to parse a relationship marker.
    *
    * Relationship markers look like: `||--o{`, `}|..|{`, `||--|{`, etc.
    *
    * Left side cardinality: `||` (exactly-one), `o|` (zero-or-one), `}|` (one-or-more), `}o` (zero-or-more) Line style: `--` (identifying) or `..` (non-identifying) Right side cardinality: `||`
    * (exactly-one), `|o` (zero-or-one), `|{` (one-or-more), `o{` (zero-or-more)
    *
    * @return
    *   Some((roleA, identification, roleB)) or None
    */
  private def tryParseRelationship(scanner: Scanner): Option[(Cardinality, Identification, Cardinality)] = boundary {
    val saved = scanner.save()

    // Read left cardinality (2 chars)
    if (scanner.pos + 6 > scanner.input.length) {
      scanner.restore(saved)
      break(None)
    }

    val leftCard = parseLeftCardinality(scanner)
    if (leftCard.isEmpty) {
      scanner.restore(saved)
      break(None)
    }

    // Read line type (2 chars: -- or ..)
    val lineType = if (!scanner.isEof && (scanner.peek() == '-' || scanner.peek() == '.')) {
      val c1 = scanner.advance()
      if (scanner.isEof) { scanner.restore(saved); break(None) }
      val c2 = scanner.advance()
      if (c1 == '-' && c2 == '-') Identification.Identifying
      else if (c1 == '.' && c2 == '.') Identification.NonIdentifying
      else { scanner.restore(saved); break(None) }
    } else {
      scanner.restore(saved)
      break(None)
    }

    // Read right cardinality (2 chars)
    val rightCard = parseRightCardinality(scanner)
    if (rightCard.isEmpty) {
      scanner.restore(saved)
      break(None)
    }

    Some((leftCard.get, lineType, rightCard.get))
  }

  /** Parses left cardinality markers. */
  private def parseLeftCardinality(scanner: Scanner): Option[Cardinality] = boundary {
    if (scanner.pos + 2 > scanner.input.length) break(None)
    val c1 = scanner.peek()
    val c2 = scanner.peekAt(1)

    val card = (c1, c2) match {
      case ('|', '|') => Some(Cardinality.ExactlyOne)
      case ('o', '|') => Some(Cardinality.ZeroOrOne)
      case ('}', '|') => Some(Cardinality.OneOrMore)
      case ('}', 'o') => Some(Cardinality.ZeroOrMore)
      case _          => None
    }

    if (card.isDefined) {
      scanner.advance()
      scanner.advance()
    }

    card
  }

  /** Parses right cardinality markers. */
  private def parseRightCardinality(scanner: Scanner): Option[Cardinality] = boundary {
    if (scanner.pos + 2 > scanner.input.length) break(None)
    val c1 = scanner.peek()
    val c2 = scanner.peekAt(1)

    val card = (c1, c2) match {
      case ('|', '|') => Some(Cardinality.ExactlyOne)
      case ('|', 'o') => Some(Cardinality.ZeroOrOne)
      case ('|', '{') => Some(Cardinality.OneOrMore)
      case ('o', '{') => Some(Cardinality.ZeroOrMore)
      case _          => None
    }

    if (card.isDefined) {
      scanner.advance()
      scanner.advance()
    }

    card
  }

  /** Tries to parse a title line. */
  private def tryParseTitle(scanner: Scanner, db: ErDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("title")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.title = readTextUntilNewline(scanner).trim
    true
  }

  /** Tries to parse accTitle. */
  private def tryParseAccTitle(scanner: Scanner, db: ErDb): Boolean = boundary {
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

  /** Tries to parse accDescr. */
  private def tryParseAccDescr(scanner: Scanner, db: ErDb): Boolean = boundary {
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
    }
    scanner.restore(saved)
    false
  }

  /** Reads an identifier (letters, digits, underscores, hyphens). */
  private def readIdentifier(scanner: Scanner): String = boundary {
    val sb = new StringBuilder()
    while (!scanner.isEof) {
      val c = scanner.peek()
      if (c.isLetterOrDigit || c == '_' || c == '-') {
        sb.append(scanner.advance())
      } else {
        break(sb.toString)
      }
    }
    sb.toString
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
