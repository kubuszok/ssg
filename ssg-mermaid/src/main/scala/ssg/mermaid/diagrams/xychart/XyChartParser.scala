/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/xychart/xychart.langium (grammar)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces Langium-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; XyChartDb for accumulation
 *   Renames: Langium grammar -> XyChartParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package xychart

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid XY chart syntax.
  *
  * Supported syntax:
  *   - `xychart-beta` — chart header (optionally `horizontal`)
  *   - `title "My Chart"` — chart title
  *   - `x-axis "Label" [cat1, cat2, ...]` — x-axis with optional categories
  *   - `x-axis "Label" min --> max` — x-axis with numeric range
  *   - `y-axis "Label" min --> max` — y-axis with numeric range
  *   - `bar [v1, v2, ...]` — bar data series
  *   - `line [v1, v2, ...]` — line data series
  */
object XyChartParser {

  /** Parses Mermaid XY chart source text into an [[XyChartDb]]. */
  def parse(input: String): XyChartDb = {
    val db      = new XyChartDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()

    // Parse header
    if (!scanner.matchStrIgnoreCase("xychart-beta")) {
      throw new ParseException("Expected 'xychart-beta' keyword", scanner.line, scanner.col)
    }
    // Optional horizontal keyword
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() != '\n') {
      scanner.matchStrIgnoreCase("horizontal")
    }
    skipToNewline(scanner)

    // Parse body
    parseBody(scanner, db)

    db
  }

  private def cleanInput(input: String): String = {
    var s = input
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    s = s.replaceAll("%%[^\n]*", "")
    s
  }

  private def parseBody(scanner: Scanner, db: XyChartDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (tryParseTitle(scanner, db)) {
        // parsed
      } else if (tryParseAccTitle(scanner, db)) {
        // parsed
      } else if (tryParseAccDescr(scanner, db)) {
        // parsed
      } else if (tryParseXAxis(scanner, db)) {
        // parsed
      } else if (tryParseYAxis(scanner, db)) {
        // parsed
      } else if (tryParseBar(scanner, db)) {
        // parsed
      } else if (tryParseLine(scanner, db)) {
        // parsed
      } else {
        skipToNewline(scanner)
      }
    }
  }

  private def tryParseTitle(scanner: Scanner, db: XyChartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("title")) { break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == '"') {
      db.title = scanner.readQuotedString()
    } else {
      db.title = readTextUntilNewline(scanner).trim
    }
    true
  }

  private def tryParseAccTitle(scanner: Scanner, db: XyChartDb): Boolean = boundary {
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

  private def tryParseAccDescr(scanner: Scanner, db: XyChartDb): Boolean = boundary {
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

  private def tryParseXAxis(scanner: Scanner, db: XyChartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("x-axis")) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()

    // Optional label (quoted or plain word)
    if (!scanner.isEof && scanner.peek() == '"') {
      db.xAxisLabel = scanner.readQuotedString()
      scanner.skipWhitespace()
    } else if (!scanner.isEof && scanner.peek() != '[' && !scanner.peek().isDigit && scanner.peek() != '-' && scanner.peek() != '\n') {
      // Plain text label (single word, stops before digits, [, or -->)
      db.xAxisLabel = readPlainLabel(scanner)
      scanner.skipWhitespace()
    }

    // Check for categories [cat1, cat2, ...]
    if (!scanner.isEof && scanner.peek() == '[') {
      scanner.advance() // consume [
      parseCategories(scanner, db.xAxisCategories)
    } else if (!scanner.isEof && (scanner.peek().isDigit || scanner.peek() == '-' || scanner.peek() == '.')) {
      // Numeric range: min --> max
      db.xAxisMin = readNumberOrDot(scanner)
      scanner.skipWhitespace()
      scanner.matchStr("-->")
      scanner.skipWhitespace()
      db.xAxisMax = readNumberOrDot(scanner)
    }

    skipToNewline(scanner)
    true
  }

  private def tryParseYAxis(scanner: Scanner, db: XyChartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("y-axis")) { scanner.restore(saved); break(false) }
    scanner.skipWhitespace()

    // Optional label (quoted or plain word)
    if (!scanner.isEof && scanner.peek() == '"') {
      db.yAxisLabel = scanner.readQuotedString()
      scanner.skipWhitespace()
    } else if (!scanner.isEof && !scanner.peek().isDigit && scanner.peek() != '-' && scanner.peek() != '.' && scanner.peek() != '\n') {
      // Plain text label (single word, stops before digits)
      db.yAxisLabel = readPlainLabel(scanner)
      scanner.skipWhitespace()
    }

    // Check for numeric range: min --> max
    if (!scanner.isEof && (scanner.peek().isDigit || scanner.peek() == '-' || scanner.peek() == '.')) {
      db.yAxisMin = readNumberOrDot(scanner)
      scanner.skipWhitespace()
      scanner.matchStr("-->")
      scanner.skipWhitespace()
      db.yAxisMax = readNumberOrDot(scanner)
    }

    skipToNewline(scanner)
    true
  }

  /** Reads a number, handling .NNN format (e.g., .34 -> 0.34). */
  private def readNumberOrDot(scanner: Scanner): Double =
    if (!scanner.isEof && scanner.peek() == '.') {
      scanner.advance()
      val sb = new StringBuilder("0.")
      while (!scanner.isEof && scanner.peek().isDigit) sb.append(scanner.advance())
      sb.toString.toDouble
    } else {
      scanner.readNumber()
    }

  /** Reads a plain (unquoted) label word for axis — stops at whitespace, digits, or special chars. The digit stop allows the parser to handle `yAxisName 45.5 --> 33` format.
    */
  private def readPlainLabel(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (
      !scanner.isEof && scanner.peek() != '\n' && scanner.peek() != '[' &&
      !(scanner.peek().isDigit && sb.nonEmpty) &&
      !(sb.nonEmpty && scanner.peek() == ' ')
    )
      sb.append(scanner.advance())
    sb.toString.trim
  }

  /** Reads a series name (bar/line) — allows digits within the name, stops at space or `[`. */
  private def readSeriesName(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (
      !scanner.isEof && scanner.peek() != '\n' && scanner.peek() != '[' &&
      !(sb.nonEmpty && scanner.peek() == ' ')
    )
      sb.append(scanner.advance())
    sb.toString.trim
  }

  private def tryParseBar(scanner: Scanner, db: XyChartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("bar")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()

    // Optional name (quoted or plain word)
    val name = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else if (!scanner.isEof && scanner.peek() != '[' && scanner.peek() != '\n') {
      readSeriesName(scanner)
    } else {
      ""
    }
    scanner.skipWhitespace()

    // Data values [v1, v2, ...]
    if (!scanner.isEof && scanner.peek() == '[') {
      scanner.advance()
      val data = parseNumberList(scanner)
      db.addBarData(name, data)
    }

    skipToNewline(scanner)
    true
  }

  private def tryParseLine(scanner: Scanner, db: XyChartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("line")) { scanner.restore(saved); break(false) }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved); break(false)
    }
    scanner.skipWhitespace()

    // Optional name (quoted or plain word)
    val name = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else if (!scanner.isEof && scanner.peek() != '[' && scanner.peek() != '\n') {
      readSeriesName(scanner)
    } else {
      ""
    }
    scanner.skipWhitespace()

    // Data values [v1, v2, ...]
    if (!scanner.isEof && scanner.peek() == '[') {
      scanner.advance()
      val data = parseNumberList(scanner)
      db.addLineData(name, data)
    }

    skipToNewline(scanner)
    true
  }

  private def parseCategories(scanner: Scanner, buffer: mutable.ArrayBuffer[String]): Unit = boundary {
    while (!scanner.isEof && scanner.peek() != ']') {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof || scanner.peek() == ']') break()
      if (scanner.peek() == ',') { scanner.advance(); scanner.skipWhitespaceAndNewlines() }
      if (scanner.isEof || scanner.peek() == ']') break()

      val cat = if (scanner.peek() == '"') {
        scanner.readQuotedString()
      } else {
        val sb = new StringBuilder()
        while (!scanner.isEof && scanner.peek() != ',' && scanner.peek() != ']' && scanner.peek() != '\n')
          sb.append(scanner.advance())
        sb.toString.trim
      }
      if (cat.nonEmpty) buffer += cat
    }
    if (!scanner.isEof && scanner.peek() == ']') scanner.advance()
  }

  private def parseNumberList(scanner: Scanner): Seq[Double] = boundary {
    val data = mutable.ArrayBuffer.empty[Double]
    while (!scanner.isEof && scanner.peek() != ']') {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof || scanner.peek() == ']') { if (!scanner.isEof) scanner.advance(); break(data.toSeq) }
      if (scanner.peek() == ',') { scanner.advance(); scanner.skipWhitespaceAndNewlines() }
      if (scanner.isEof || scanner.peek() == ']') { if (!scanner.isEof) scanner.advance(); break(data.toSeq) }

      if (scanner.peek() == '+') {
        scanner.advance() // skip + sign, readNumber handles the digits
        data += scanner.readNumber()
      } else if (scanner.peek() == '.') {
        // Handle .33 format - read as 0.33
        scanner.advance() // consume .
        val sb = new StringBuilder("0.")
        while (!scanner.isEof && scanner.peek().isDigit) sb.append(scanner.advance())
        data += sb.toString.toDouble
      } else if (scanner.peek().isDigit || scanner.peek() == '-') {
        data += scanner.readNumber()
      } else {
        scanner.advance() // skip unexpected char
      }
    }
    if (!scanner.isEof && scanner.peek() == ']') scanner.advance()
    data.toSeq
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
