/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/user-journey/parser/journey.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; JourneyDb for accumulation
 *   Renames: journey.jison -> JourneyParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package journey

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid user journey syntax.
  *
  * Supported syntax:
  *   - `journey` — diagram header
  *   - `title My Journey` — optional title
  *   - `section Getting Started` — section definition
  *   - `Task Name: score: actor1, actor2` — task definition
  */
object JourneyParser {

  /** Parses Mermaid user journey source text into a [[JourneyDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated JourneyDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): JourneyDb = {
    val db      = new JourneyDb
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

  /** Parses the journey keyword. */
  private def parseHeader(scanner: Scanner): Unit = {
    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("journey")) {
      throw new ParseException("Expected 'journey' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
  }

  /** Parses the body: title, sections, and task definitions. */
  private def parseBody(scanner: Scanner, db: JourneyDb): Unit = boundary {
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
  private def parseLine(scanner: Scanner, db: JourneyDb): Unit = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') break()

    if (tryParseTitle(scanner, db)) break()
    if (tryParseSection(scanner, db)) break()
    if (tryParseAccTitle(scanner, db)) break()
    if (tryParseAccDescr(scanner, db)) break()

    // Default: task definition
    parseTaskLine(scanner, db)
  }

  /** Tries to parse `title ...`. */
  private def tryParseTitle(scanner: Scanner, db: JourneyDb): Boolean = boundary {
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
  private def tryParseSection(scanner: Scanner, db: JourneyDb): Boolean = boundary {
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
  private def tryParseAccTitle(scanner: Scanner, db: JourneyDb): Boolean = boundary {
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
  private def tryParseAccDescr(scanner: Scanner, db: JourneyDb): Boolean = boundary {
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

  /** Parses a task line: `Task Name: score: actor1, actor2`. */
  private def parseTaskLine(scanner: Scanner, db: JourneyDb): Unit = {
    val lineText = readTextUntilNewline(scanner).trim
    if (lineText.nonEmpty) {
      // Split on colons: name : score : actors
      val parts = lineText.split(":").map(_.trim)

      if (parts.length >= 2) {
        val taskName = parts(0)
        val scoreStr = parts(1).trim
        val score    =
          try
            scoreStr.toInt
          catch {
            case _: NumberFormatException => 3
          }

        val taskActors = if (parts.length >= 3) {
          parts(2).split(",").map(_.trim).filter(_.nonEmpty)
        } else {
          Array.empty[String]
        }

        db.addTask(taskName, score, taskActors)
      } else {
        // No colon — just task name with default score
        db.addTask(lineText, 3, Array.empty)
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
