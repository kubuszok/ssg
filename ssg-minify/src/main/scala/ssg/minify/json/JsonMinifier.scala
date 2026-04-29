/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JSON minification — removes whitespace and comments outside string values.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb (json-minify gem)
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: json-minify gem → ssg.minify.json.JsonMinifier
 *   Convention: Pure Scala 3, state-machine based, cross-platform
 *   Idiom: No external dependencies, handles // and block comments
 *
 * Covenant: full-port
 * Covenant-ruby-reference: lib/jekyll-minifier.rb
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 5422b3570321668b419ec8271391a029f385c390
 */
package ssg
package minify
package json

import scala.util.boundary
import scala.util.boundary.break

object JsonMinifier {

  /** Minify JSON by removing whitespace and comments outside string values.
    *
    * Handles:
    *   - Whitespace removal outside strings
    *   - Single-line comment removal (`//`)
    *   - Block comment removal
    *   - Preserves all content inside quoted strings (including escape sequences)
    */
  def minify(input: String): String =
    if (input.isEmpty) {
      input
    } else {
      doMinify(input)
    }

  private def doMinify(input: String): String = {
    val len = input.length
    val sb  = new StringBuilder(len)
    var i   = 0

    while (i < len) {
      val c = input.charAt(i)

      if (c == '"') {
        // String literal — copy verbatim including escape sequences
        i = copyString(input, i, len, sb)
      } else if (c == '/' && i + 1 < len && input.charAt(i + 1) == '/') {
        // Single-line comment — skip to end of line
        i = skipLineComment(input, i, len)
      } else if (c == '/' && i + 1 < len && input.charAt(i + 1) == '*') {
        // Block comment — skip to */
        i = skipBlockComment(input, i, len)
      } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        // Whitespace outside string — skip
        i += 1
      } else {
        sb.append(c)
        i += 1
      }
    }

    sb.toString()
  }

  /** Copy a quoted string verbatim. Returns index after closing quote. */
  private def copyString(input: String, start: Int, len: Int, sb: StringBuilder): Int = {
    sb.append('"')
    var i       = start + 1
    var escaped = false
    boundary {
      while (i < len) {
        val c = input.charAt(i)
        sb.append(c)
        if (escaped) {
          escaped = false
        } else if (c == '\\') {
          escaped = true
        } else if (c == '"') {
          i += 1
          break()
        }
        i += 1
      }
    }
    i
  }

  /** Skip a single-line comment (// to end of line). Returns index after newline. */
  private def skipLineComment(input: String, start: Int, len: Int): Int = {
    var i = start + 2
    while (i < len && input.charAt(i) != '\n')
      i += 1
    if (i < len) i + 1 else i
  }

  /** Skip a block comment. Returns index after closing star-slash. */
  private def skipBlockComment(input: String, start: Int, len: Int): Int = {
    var i = start + 2
    boundary {
      while (i + 1 < len) {
        if (input.charAt(i) == '*' && input.charAt(i + 1) == '/') {
          i += 2
          break()
        }
        i += 1
      }
    }
    i
  }
}
