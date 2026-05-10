/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/state/parser/stateDiagram.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; StateDb for accumulation
 *   Renames: stateDiagram.jison -> StateParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package state

import ssg.commons.Nullable
import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid state diagram syntax.
  *
  * Parses the grammar defined in `stateDiagram.jison`, producing a populated [[StateDb]].
  *
  * Supported syntax:
  *   - `stateDiagram-v2` or `stateDiagram` — diagram header
  *   - `[*] --> s1` — start/end state transitions
  *   - `s1 --> s2` — basic transitions
  *   - `s1 --> s2 : label` — labeled transitions
  *   - `state "Name" as s1` — aliased state
  *   - `state fork <<fork>>` — special state types
  *   - `state "Outer" { ... }` — composite (nested) states
  *   - `direction TB` — direction override
  *   - `note left of s1 : text` — notes
  */
object StateParser {

  /** Parses Mermaid state diagram source text into a [[StateDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated StateDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): StateDb = {
    val db      = new StateDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()

    // Parse header
    parseHeader(scanner, db)

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

  /** Parses the stateDiagram-v2 or stateDiagram keyword. */
  private def parseHeader(scanner: Scanner, db: StateDb): Unit = {
    scanner.skipWhitespaceAndNewlines()

    if (scanner.matchStrIgnoreCase("stateDiagram-v2")) {
      // v2 variant
    } else if (scanner.matchStrIgnoreCase("stateDiagram")) {
      // v1 variant
    } else {
      throw new ParseException("Expected 'stateDiagram' or 'stateDiagram-v2' keyword", scanner.line, scanner.col)
    }

    skipToNewline(scanner)
  }

  /** Parses the body: transitions, states, notes, and directives. */
  private def parseBody(scanner: Scanner, db: StateDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      // Skip comments
      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (scanner.peek() == ';') {
        scanner.advance()
      } else if (scanner.peek() == '}') {
        // End of composite state
        scanner.advance()
        db.popState()
      } else {
        parseStatement(scanner, db)
      }
    }
  }

  /** Parses a single statement. */
  private def parseStatement(scanner: Scanner, db: StateDb): Unit = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') break()

    if (tryParseDirection(scanner, db)) break()
    if (tryParseStateDeclaration(scanner, db)) break()
    if (tryParseNote(scanner, db)) break()
    if (tryParseClassDef(scanner, db)) break()
    if (tryParseClassStatement(scanner, db)) break()
    if (tryParseAccTitle(scanner, db)) break()
    if (tryParseAccDescr(scanner, db)) break()
    if (tryParseTitle(scanner, db)) break()

    // Default: try transition (stateId --> stateId) or bare state
    parseTransitionOrState(scanner, db)
  }

  /** Tries to parse `direction TB/LR/RL/BT`. */
  private def tryParseDirection(scanner: Scanner, db: StateDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("direction")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    val dir = readIdentifier(scanner)
    if (dir.nonEmpty) {
      db.direction = dir.toUpperCase
    }
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `state` keyword: aliases, special types, and composites. */
  private def tryParseStateDeclaration(scanner: Scanner, db: StateDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("state")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()

    if (scanner.isEof) {
      scanner.restore(saved)
      break(false)
    }

    // Check for quoted name: state "Name" as id
    if (scanner.peek() == '"') {
      val description = scanner.readQuotedString()
      scanner.skipWhitespace()

      // Check for "as" keyword
      if (scanner.matchStrIgnoreCase("as")) {
        scanner.skipWhitespace()
        val id = readIdentifier(scanner)
        db.addState(id, Nullable(description))
      }

      scanner.skipWhitespace()
      // Check for composite: {
      if (!scanner.isEof && scanner.peek() == '{') {
        scanner.advance()
        // Find the state we just created or get the id
        // The last added state is the composite parent
        // Push this state for nesting
        val stateKeys = db.states.keys.toArray
        if (stateKeys.nonEmpty) {
          db.pushState(stateKeys.last)
        }
      }

      skipToNewline(scanner)
      break(true)
    }

    // state id <<type>>
    val id = readIdentifier(scanner)
    if (id.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Check for <<type>>
    if (!scanner.isEof && scanner.peek() == '<' && scanner.peekAt(1) == '<') {
      scanner.advance() // <
      scanner.advance() // <
      val specialType = readIdentifier(scanner)
      if (!scanner.isEof && scanner.peek() == '>' && scanner.peekAt(1) == '>') {
        scanner.advance() // >
        scanner.advance() // >
      }
      db.addSpecialState(id, specialType)
      skipToNewline(scanner)
      break(true)
    }

    // Check for composite: {
    if (!scanner.isEof && scanner.peek() == '{') {
      scanner.advance()
      db.addState(id)
      db.pushState(id)
      break(true)
    }

    // Just a state declaration
    db.addState(id)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `note left of stateId : text` or `note right of stateId : text`. */
  private def tryParseNote(scanner: Scanner, db: StateDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("note")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()

    // Parse position: "left of" or "right of"
    val position = if (scanner.matchStrIgnoreCase("left")) {
      scanner.skipWhitespace()
      scanner.matchStrIgnoreCase("of")
      "left of"
    } else if (scanner.matchStrIgnoreCase("right")) {
      scanner.skipWhitespace()
      scanner.matchStrIgnoreCase("of")
      "right of"
    } else {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()
    val stateId = readIdentifier(scanner)
    if (stateId.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()
    // Expect colon
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      val noteText = readTextUntilNewline(scanner).trim
      db.addState(stateId)
      db.addNote(stateId, noteText, position)
    }

    skipToNewline(scanner)
    true
  }

  /** Parses a transition (A --> B : label) or bare state reference. */
  private def parseTransitionOrState(scanner: Scanner, db: StateDb): Unit = boundary {
    val fromId = readStateId(scanner)
    if (fromId.isEmpty) {
      skipToNewline(scanner)
      break()
    }

    scanner.skipWhitespace()

    // Check for transition arrow: -->
    if (!scanner.isEof && scanner.peek() == '-' && scanner.peekAt(1) == '-' && scanner.peekAt(2) == '>') {
      scanner.advance() // -
      scanner.advance() // -
      scanner.advance() // >

      scanner.skipWhitespace()

      val toId = readStateId(scanner)
      if (toId.isEmpty) {
        db.addState(fromId)
        skipToNewline(scanner)
        break()
      }

      // Optional label: : text
      scanner.skipWhitespace()
      var label = ""
      if (!scanner.isEof && scanner.peek() == ':') {
        scanner.advance()
        scanner.skipWhitespace()
        label = readTextUntilNewline(scanner).trim
      }

      db.addTransition(fromId, toId, label)
    } else if (!scanner.isEof && scanner.peek() == ':') {
      // State with description: stateId : description text
      scanner.advance()
      scanner.skipWhitespace()
      val desc = readTextUntilNewline(scanner).trim
      db.addState(fromId, Nullable(desc))
    } else {
      // Just a bare state reference
      db.addState(fromId)
      skipToNewline(scanner)
    }
  }

  /** Reads a state ID, which can be `[*]` or a regular identifier. */
  private def readStateId(scanner: Scanner): String =
    if (!scanner.isEof && scanner.peek() == '[' && scanner.peekAt(1) == '*' && scanner.peekAt(2) == ']') {
      scanner.advance() // [
      scanner.advance() // *
      scanner.advance() // ]
      "[*]"
    } else {
      readIdentifier(scanner)
    }

  /** Tries to parse classDef. */
  private def tryParseClassDef(scanner: Scanner, db: StateDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("classDef")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    // Skip the rest — classDef is not critical for rendering
    skipToNewline(scanner)
    true
  }

  /** Tries to parse class statement. */
  private def tryParseClassStatement(scanner: Scanner, db: StateDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("class")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    val ids = readIdentifier(scanner)
    scanner.skipWhitespace()
    val className = readIdentifier(scanner)
    db.setClass(ids, className)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse title. */
  private def tryParseTitle(scanner: Scanner, db: StateDb): Boolean = boundary {
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
  private def tryParseAccTitle(scanner: Scanner, db: StateDb): Boolean = boundary {
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
  private def tryParseAccDescr(scanner: Scanner, db: StateDb): Boolean = boundary {
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
