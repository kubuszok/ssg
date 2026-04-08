/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/sass.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: sass.dart -> SassParser.scala
 *   Idiom: Indented-syntax support is implemented as an indentation-based
 *     preprocessor that rewrites the source into the equivalent SCSS form,
 *     then delegates parsing to ScssParser. This keeps the Sass and SCSS
 *     code paths sharing one statement parser. The override hooks
 *     (`styleRuleSelector`, `expectStatementSeparator`, `atEndOfStatement`,
 *     `lookingAtChildren`, `scanElse`, `children`, `statements`) match the
 *     ScssParser pattern but are not exercised because `parse()` is itself
 *     overridden to delegate.
 */
package ssg
package sass
package parse

import ssg.sass.Nullable
import ssg.sass.ast.sass.{ Interpolation, Statement, Stylesheet }

/** A parser for the whitespace-sensitive indented Sass syntax.
  *
  * Implements indented Sass by translating the source to SCSS in a preprocessing pass, then delegating to [[ScssParser]]. This handles the common cases — variables, declarations, nested style rules,
  * simple
  * @-rules,
  *   `//` and `/* */` comments — while staying compact.
  */
class SassParser(
  contents:       String,
  url:            Nullable[String] = Nullable.Null,
  parseSelectors: Boolean = false
) extends StylesheetParser(contents, url, parseSelectors) {

  override def indented:           Boolean = true
  override def currentIndentation: Int     = 0

  /** Override the public entry point: translate indented input to SCSS, then parse via the SCSS parser so all of StylesheetParser's machinery is reused.
    */
  override def parse(): Stylesheet = {
    val translated = SassParser.indentedToScss(contents)
    new ScssParser(translated, url, parseSelectors).parse()
  }

  // The hooks below match ScssParser's overrides for symmetry but are not
  // exercised because `parse()` is itself overridden.

  override protected def styleRuleSelector(): Interpolation =
    throw new UnsupportedOperationException("SassParser uses preprocessing; styleRuleSelector unused")

  override protected def expectStatementSeparator(name: Nullable[String] = Nullable.Null): Unit =
    throw new UnsupportedOperationException("SassParser uses preprocessing; expectStatementSeparator unused")

  override protected def atEndOfStatement(): Boolean =
    throw new UnsupportedOperationException("SassParser uses preprocessing; atEndOfStatement unused")

  override protected def lookingAtChildren(): Boolean =
    throw new UnsupportedOperationException("SassParser uses preprocessing; lookingAtChildren unused")

  override protected def scanElse(ifIndentation: Int): Boolean =
    throw new UnsupportedOperationException("SassParser uses preprocessing; scanElse unused")

  override protected def children(child: () => Statement): List[Statement] =
    throw new UnsupportedOperationException("SassParser uses preprocessing; children unused")

  override protected def statements(statement: () => Nullable[Statement]): List[Statement] =
    throw new UnsupportedOperationException("SassParser uses preprocessing; statements unused")
}

object SassParser {

  /** Translate an indented Sass source string into the equivalent SCSS source.
    *
    * Algorithm: walk lines, track an indentation stack. A line whose indentation is greater than the previous non-blank line opens a new block (`{`), greater closes the appropriate number of blocks
    * (`}`). Statements are terminated with `;`. Blank lines and `//` line comments are passed through. `/* ... */` block comments are passed through verbatim across lines.
    *
    * Limitations: no line continuations, no trailing-comma selectors that span lines, no `===`/`!default` quirks beyond what SCSS already accepts.
    */
  def indentedToScss(source: String): String = {
    val out      = new StringBuilder()
    val rawLines = source.split("\n", -1).toList
    // Stack of indentation levels for currently open `{` blocks. The
    // bottom (level 0) is the implicit top-level block; we don't emit
    // braces for it.
    val stack = scala.collection.mutable.ArrayBuffer[Int](-1)

    // Detect a `/* ... */` block comment that spans multiple lines, so we
    // pass it through verbatim and don't try to interpret indentation
    // inside it.
    var inBlockComment = false

    var i = 0
    while (i < rawLines.length) {
      val line = rawLines(i)
      if (inBlockComment) {
        out.append(line).append('\n')
        if (line.contains("*/")) inBlockComment = false
        i += 1
      } else {
        val indent = line.takeWhile(c => c == ' ' || c == '\t').length
        val rest   = line.substring(indent)
        if (rest.isEmpty) {
          // blank line — pass through with no statement effect
          out.append('\n')
          i += 1
        } else if (rest.startsWith("//")) {
          // silent comment — pass through
          out.append(line).append('\n')
          i += 1
        } else if (rest.startsWith("/*")) {
          out.append(line).append('\n')
          if (!rest.contains("*/")) inBlockComment = true
          i += 1
        } else {
          // Close any blocks whose indent is >= current.
          while (stack.length > 1 && stack(stack.length - 1) >= indent) {
            stack.remove(stack.length - 1)
            out.append("}\n")
          }
          // Look ahead: is the next non-blank, non-comment line indented
          // more than this one? If so, this line opens a block.
          var j           = i + 1
          var childIndent = -1
          var scanning    = true
          while (scanning && j < rawLines.length) {
            val nl      = rawLines(j)
            val nIndent = nl.takeWhile(c => c == ' ' || c == '\t').length
            val nRest   = nl.substring(nIndent)
            if (nRest.isEmpty) {
              j += 1
            } else if (nRest.startsWith("//")) {
              j += 1
            } else {
              childIndent = nIndent
              scanning = false
            }
          }
          val opensBlock = childIndent > indent
          if (opensBlock) {
            out.append(rest).append(" {\n")
            stack += indent
          } else {
            out.append(rest).append(";\n")
          }
          i += 1
        }
      }
    }
    // Close any remaining open blocks.
    while (stack.length > 1) {
      stack.remove(stack.length - 1)
      out.append("}\n")
    }
    out.toString()
  }
}
