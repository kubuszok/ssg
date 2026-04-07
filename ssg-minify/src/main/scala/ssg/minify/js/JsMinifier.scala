/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Basic JavaScript minification — removes comments and collapses whitespace.
 *
 * Does NOT perform AST-based optimizations (variable renaming, dead code
 * elimination, tree shaking). A full Terser port is planned as a separate
 * ssg-js module.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb (terser gem)
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: terser gem → ssg.minify.js.JsMinifier (basic stopgap)
 *   Convention: Pure Scala 3, state-machine based, cross-platform
 *   Idiom: Tracks string/template-literal/regex-literal context
 *   Gap: Intentional stopgap. No mangling, DCE, constant folding, inlining,
 *     scope analysis, or source maps. Real minification is delegated via the
 *     JsCompressor SPI to ssg.js.TerserJsCompressor (wired by
 *     TerserJsCompressorAdapter in the ssg aggregator module). Note that the
 *     Terser bridge itself is currently parse+re-emit until ISS-031/ISS-032
 *     are resolved. See docs/architecture/terser-port.md.
 *   Audited: 2026-04-07 (minor_issues)
 */
package ssg
package minify
package js

import scala.util.boundary
import scala.util.boundary.break

/** Basic JavaScript minifier. Implements JsCompressor for use with HtmlMinifier. */
object JsMinifier extends JsCompressor {

  /** Minify JavaScript by removing comments and collapsing whitespace.
    *
    * Uses a state machine to track string (`'`, `"`, `` ` ``) and regex literal contexts so that comments and whitespace inside literals are preserved.
    */
  def minify(input: String, options: JsMinifyOptions = JsMinifyOptions.Defaults): String =
    if (input.isEmpty) {
      input
    } else {
      doMinify(input, options)
    }

  /** JsCompressor implementation — delegates to minify with default options. */
  override def compress(input: String): String = minify(input)

  private def doMinify(input: String, options: JsMinifyOptions): String = {
    val len = input.length
    val sb  = new StringBuilder(len)
    var i   = 0

    while (i < len) {
      val c = input.charAt(i)

      if (c == '\'' || c == '"') {
        // String literal — copy verbatim
        i = copyStringLiteral(input, i, len, sb)
      } else if (c == '`') {
        // Template literal — copy verbatim (including ${} expressions)
        i = copyTemplateLiteral(input, i, len, sb)
      } else if (c == '/' && i + 1 < len && input.charAt(i + 1) == '/') {
        // Single-line comment
        if (options.removeComments) {
          i = skipLineComment(input, i, len)
          // Preserve a newline so statements separated by comments don't merge
          if (sb.nonEmpty && sb.last != '\n') {
            sb.append('\n')
          }
        } else {
          sb.append(c)
          i += 1
        }
      } else if (c == '/' && i + 1 < len && input.charAt(i + 1) == '*') {
        // Block comment
        if (options.removeComments) {
          i = skipBlockComment(input, i, len)
          // Preserve a space so tokens don't merge
          if (sb.nonEmpty && sb.last != ' ' && sb.last != '\n') {
            sb.append(' ')
          }
        } else {
          sb.append(c)
          i += 1
        }
      } else if (c == '/' && isRegexContext(input, sb)) {
        // Regex literal — copy verbatim
        i = copyRegexLiteral(input, i, len, sb)
      } else if (isWhitespace(c) && options.collapseWhitespace) {
        // Collapse whitespace runs
        val wsHasNewline = hasNewlineInRun(input, i, len)
        while (i < len && isWhitespace(input.charAt(i)))
          i += 1
        // Preserve newlines for ASI (automatic semicolon insertion)
        if (wsHasNewline && sb.nonEmpty && needsNewline(sb, input, i, len)) {
          sb.append('\n')
        } else if (sb.nonEmpty && needsSpace(sb, input, i, len)) {
          sb.append(' ')
        }
      } else {
        sb.append(c)
        i += 1
      }
    }

    val result = sb.toString()
    if (options.collapseWhitespace) result.trim else result
  }

  /** Copy a string literal verbatim. Returns index after closing quote. */
  private def copyStringLiteral(input: String, start: Int, len: Int, sb: StringBuilder): Int = {
    val quote = input.charAt(start)
    sb.append(quote)
    var i = start + 1
    boundary {
      while (i < len) {
        val c = input.charAt(i)
        sb.append(c)
        if (c == '\\' && i + 1 < len) {
          i += 1
          sb.append(input.charAt(i))
        } else if (c == quote) {
          i += 1
          break()
        }
        i += 1
      }
    }
    i
  }

  /** Copy a template literal verbatim, including ${...} expressions. */
  private def copyTemplateLiteral(input: String, start: Int, len: Int, sb: StringBuilder): Int = {
    sb.append('`')
    var i = start + 1
    boundary {
      while (i < len) {
        val c = input.charAt(i)
        if (c == '\\' && i + 1 < len) {
          sb.append(c)
          i += 1
          sb.append(input.charAt(i))
        } else if (c == '$' && i + 1 < len && input.charAt(i + 1) == '{') {
          // Template expression — copy including nested braces
          sb.append(c)
          i += 1
          sb.append(input.charAt(i)) // the '{'
          i += 1
          var depth = 1
          while (i < len && depth > 0) {
            val ec = input.charAt(i)
            sb.append(ec)
            if (ec == '{') depth += 1
            else if (ec == '}') depth -= 1
            if (depth > 0) i += 1
          }
          if (i < len) i += 1 // move past closing '}'
        } else if (c == '`') {
          sb.append(c)
          i += 1
          break()
        } else {
          sb.append(c)
          i += 1
        }
      }
    }
    i
  }

  /** Copy a regex literal verbatim, including flags. */
  private def copyRegexLiteral(input: String, start: Int, len: Int, sb: StringBuilder): Int = {
    sb.append('/')
    var i = start + 1
    boundary {
      while (i < len) {
        val c = input.charAt(i)
        if (c == '\\' && i + 1 < len) {
          sb.append(c)
          i += 1
          sb.append(input.charAt(i))
        } else if (c == '[') {
          // Character class — '/' doesn't close the regex inside [...]
          sb.append(c)
          i += 1
          while (i < len && input.charAt(i) != ']') {
            if (input.charAt(i) == '\\' && i + 1 < len) {
              sb.append(input.charAt(i))
              i += 1
            }
            sb.append(input.charAt(i))
            i += 1
          }
          if (i < len) {
            sb.append(input.charAt(i)) // the ']'
          }
        } else if (c == '/') {
          sb.append(c)
          i += 1
          // Copy regex flags (gimsuy)
          while (i < len && input.charAt(i).isLetter) {
            sb.append(input.charAt(i))
            i += 1
          }
          break()
        } else {
          sb.append(c)
        }
        i += 1
      }
    }
    i
  }

  /** Skip single-line comment. Returns index after newline (or end). */
  private def skipLineComment(input: String, start: Int, len: Int): Int = {
    var i = start + 2
    while (i < len && input.charAt(i) != '\n')
      i += 1
    if (i < len) i + 1 else i
  }

  /** Skip block comment. Returns index after closing star-slash. */
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

  /** Determine if '/' at current position is the start of a regex literal (not division). */
  private def isRegexContext(input: String, sb: StringBuilder): Boolean =
    // A '/' starts a regex if preceded by an operator, keyword, or opening bracket.
    // Heuristic: look at the last non-whitespace character in output.
    if (sb.isEmpty) true
    else {
      var j = sb.length - 1
      while (j >= 0 && (sb.charAt(j) == ' ' || sb.charAt(j) == '\n' || sb.charAt(j) == '\t'))
        j -= 1
      if (j < 0) true
      else {
        val prev = sb.charAt(j)
        // After these characters, '/' starts a regex
        prev == '=' || prev == '(' || prev == '[' || prev == '!' ||
        prev == '&' || prev == '|' || prev == '?' || prev == ':' ||
        prev == ',' || prev == ';' || prev == '{' || prev == '}' ||
        prev == '\n' || prev == '^' || prev == '~' || prev == '+' ||
        prev == '-' || prev == '*' || prev == '%'
      }
    }

  private def isWhitespace(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f'

  /** Check if a whitespace run starting at i contains a newline. */
  private def hasNewlineInRun(input: String, start: Int, len: Int): Boolean =
    boundary[Boolean] {
      var i = start
      while (i < len && isWhitespace(input.charAt(i))) {
        if (input.charAt(i) == '\n' || input.charAt(i) == '\r') {
          break(true)
        }
        i += 1
      }
      false
    }

  /** Check if a newline is needed between previous output and next input for ASI. */
  private def needsNewline(sb: StringBuilder, input: String, nextIdx: Int, len: Int): Boolean =
    if (nextIdx >= len) false
    else {
      val last = lastNonSpace(sb)
      val next = input.charAt(nextIdx)
      // Newline needed if previous token ended with an identifier/number/string-close
      // and next token starts with an identifier/number/string-open
      isIdentOrClose(last) && isIdentOrOpen(next)
    }

  /** Check if a space is needed between previous output and next input. */
  private def needsSpace(sb: StringBuilder, input: String, nextIdx: Int, len: Int): Boolean =
    if (nextIdx >= len || sb.isEmpty) false
    else {
      val last = sb.last
      val next = input.charAt(nextIdx)
      // Space needed between identifier chars, or between keywords and braces
      (last.isLetterOrDigit || last == '_' || last == '$') &&
      (next.isLetterOrDigit || next == '_' || next == '$' || next == '{' || next == '(')
    }

  private def lastNonSpace(sb: StringBuilder): Char = {
    var j = sb.length - 1
    while (j >= 0 && isWhitespace(sb.charAt(j)))
      j -= 1
    if (j >= 0) sb.charAt(j) else '\u0000'
  }

  private def isIdentOrClose(c: Char): Boolean =
    c.isLetterOrDigit || c == '_' || c == '$' || c == ')' || c == ']' || c == '"' || c == '\'' || c == '`'

  private def isIdentOrOpen(c: Char): Boolean =
    c.isLetterOrDigit || c == '_' || c == '$' || c == '(' || c == '[' || c == '"' || c == '\'' || c == '`' || c == '/' || c == '{' || c == '!'
}
