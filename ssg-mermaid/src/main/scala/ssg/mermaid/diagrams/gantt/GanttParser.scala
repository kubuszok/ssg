/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/gantt/parser/gantt.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; GanttDb for accumulation
 *   Renames: gantt.jison -> GanttParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package gantt

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid Gantt chart syntax.
  *
  * Parses the grammar defined in `gantt.jison`, producing a populated [[GanttDb]].
  *
  * Supported syntax:
  *   - `gantt` — chart header
  *   - `title My Project` — chart title
  *   - `dateFormat YYYY-MM-DD` — date format specification
  *   - `axisFormat %Y-%m-%d` — axis label format
  *   - `section Phase 1` — section definition
  *   - `Task name :id, status, startDate, duration` — task definition
  *   - `excludes weekends` — exclude days
  *   - `todayMarker` — today marker config
  *   - `inclusiveEndDates` — end date behavior
  *   - `topAxis` — axis position
  */
object GanttParser {

  /** Parses Mermaid Gantt chart source text into a [[GanttDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated GanttDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): GanttDb =
    parse(input, new GanttDb)

  /** Parses Mermaid gantt source text into the supplied [[GanttDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param db
    *   the db to parse into
    * @return
    *   the supplied GanttDb, populated
    */
  def parse(input: String, db: GanttDb): GanttDb = {
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()

    // Parse header
    parseHeader(scanner)

    // Parse body
    parseBody(scanner, db)

    // Resolve forward dependencies via the multi-pass compile. Mirrors
    // ganttDb.js:156-161 (getTasks runs compileTasks in a loop before
    // returning the tasks): a FORWARD `after`/`until` reference — whose
    // dependency is declared later in the source — only resolves here.
    db.compileTasks()

    db
  }

  /** Removes directives and comments from input. */
  private def cleanInput(input: String): String = {
    var s = input
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    s = s.replaceAll("%%[^\n]*", "")
    s
  }

  /** Parses the gantt keyword. */
  private def parseHeader(scanner: Scanner): Unit = {
    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("gantt")) {
      throw new ParseException("Expected 'gantt' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
  }

  /** Parses the body: directives, sections, and tasks. */
  private def parseBody(scanner: Scanner, db: GanttDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      // Skip comments
      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (scanner.peek() == ';') {
        scanner.advance()
      } else {
        parseLine(scanner, db)
      }
    }
  }

  /** Parses a single line. */
  private def parseLine(scanner: Scanner, db: GanttDb): Unit = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') break()

    if (tryParseTitle(scanner, db)) break()
    if (tryParseDateFormat(scanner, db)) break()
    if (tryParseAxisFormat(scanner, db)) break()
    if (tryParseTickInterval(scanner, db)) break()
    if (tryParseSection(scanner, db)) break()
    if (tryParseExcludes(scanner, db)) break()
    if (tryParseTodayMarker(scanner, db)) break()
    if (tryParseInclusiveEndDates(scanner, db)) break()
    if (tryParseTopAxis(scanner, db)) break()
    if (tryParseDisplayMode(scanner, db)) break()
    if (tryParseWeekday(scanner, db)) break()
    if (tryParseAccTitle(scanner, db)) break()
    if (tryParseAccDescr(scanner, db)) break()

    // Default: task definition
    parseTask(scanner, db)
  }

  /** Tries to parse `title ...`. */
  private def tryParseTitle(scanner: Scanner, db: GanttDb): Boolean = boundary {
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

  /** Tries to parse `dateFormat ...`. */
  private def tryParseDateFormat(scanner: Scanner, db: GanttDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("dateFormat")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.dateFormat = readTextUntilNewline(scanner).trim
    true
  }

  /** Tries to parse `axisFormat ...`. */
  private def tryParseAxisFormat(scanner: Scanner, db: GanttDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("axisFormat")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.axisFormat = readTextUntilNewline(scanner).trim
    true
  }

  /** Tries to parse `tickInterval ...`.
    *
    * Ports the `tickInterval` token (`gantt.jison:76`, `"tickInterval"\s[^#\n;]+`) and its action (`gantt.jison:143`, `yy.setTickInterval($1.substr(13))`). `substr(13)` strips the `"tickInterval "`
    * prefix, leaving the value (e.g. `1day`, `2week`). Here the whitespace after the keyword is consumed by `skipWhitespace()` and the remaining text becomes the value.
    */
  private def tryParseTickInterval(scanner: Scanner, db: GanttDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("tickInterval")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.tickInterval = readTextUntilNewline(scanner).trim
    true
  }

  /** Tries to parse `section ...`. */
  private def tryParseSection(scanner: Scanner, db: GanttDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("section")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    val name = readTextUntilNewline(scanner).trim
    db.addSection(name)
    true
  }

  /** Tries to parse `excludes ...`. */
  private def tryParseExcludes(scanner: Scanner, db: GanttDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("excludes")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.addExclude(readTextUntilNewline(scanner).trim)
    true
  }

  /** Tries to parse `todayMarker ...`. */
  private def tryParseTodayMarker(scanner: Scanner, db: GanttDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("todayMarker")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    val marker = readTextUntilNewline(scanner).trim
    if (marker.nonEmpty) db.todayMarker = marker
    true
  }

  /** Tries to parse `inclusiveEndDates`. */
  private def tryParseInclusiveEndDates(scanner: Scanner, db: GanttDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("inclusiveEndDates")) break(false)
    db.inclusiveEndDates = true
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `topAxis`. */
  private def tryParseTopAxis(scanner: Scanner, db: GanttDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("topAxis")) break(false)
    db.topAxis = true
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `displayMode ...`. */
  private def tryParseDisplayMode(scanner: Scanner, db: GanttDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("displayMode")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.displayMode = readTextUntilNewline(scanner).trim
    true
  }

  /** Tries to parse `weekday ...`. */
  private def tryParseWeekday(scanner: Scanner, db: GanttDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("weekday")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    db.weekday = readTextUntilNewline(scanner).trim
    true
  }

  /** Tries to parse accTitle. */
  private def tryParseAccTitle(scanner: Scanner, db: GanttDb): Boolean = boundary {
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
  private def tryParseAccDescr(scanner: Scanner, db: GanttDb): Boolean = boundary {
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

  /** Parses a task definition: `TaskName :data`. */
  private def parseTask(scanner: Scanner, db: GanttDb): Unit = boundary {
    // Read task name (everything before the colon)
    val lineText = readTextUntilNewline(scanner).trim
    if (lineText.isEmpty) break()

    val colonIdx = lineText.indexOf(':')
    if (colonIdx > 0) {
      val taskName = lineText.substring(0, colonIdx).trim
      val taskData = lineText.substring(colonIdx + 1).trim
      db.addTask(taskName, taskData)
    } else {
      // No colon — treat entire line as task name with defaults
      db.addTask(lineText, "")
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
