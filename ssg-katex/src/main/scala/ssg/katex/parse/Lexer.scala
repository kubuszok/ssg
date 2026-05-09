/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The Lexer class handles tokenizing the input in various ways. Since our
 * parser expects us to be able to backtrack, the lexer allows lexing from any
 * given starting point.
 *
 * Its main exposed function is the `lex` function, which takes a position to
 * lex from and a type of token to lex. It defers to the appropriate `_innerLex`
 * function.
 *
 * The various `_innerLex` functions perform the actual lexing of different
 * kinds.
 *
 * Original source: katex src/Lexer.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: Lexer -> Lexer (same)
 *   Convention: lastIndex-based regex -> java.util.regex.Matcher with region
 *   Idiom: TypeScript Record<string, number> -> mutable.Map[String, Int]
 */
package ssg
package katex
package parse

import java.util.regex.{Matcher, Pattern}

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break
import scala.util.matching.Regex

import ssg.commons.Nullable

/**
 * Trait for settings needed by the Lexer.
 * The full Settings class will be ported in a later phase; this provides the
 * minimal shape needed by the Lexer.
 */
trait SettingsLike {
  def reportNonstrict(errorCode: String, errorMsg: String): Unit
}

/* The following tokenRegex
 * - matches typical whitespace (but not NBSP etc.) using its first group
 * - does not match any control character \x00-\x1f except whitespace
 * - does not match a bare backslash
 * - matches any ASCII character except those just mentioned
 * - does not match the BMP private use area -
 * - does not match bare surrogate code units
 * - matches any BMP character except for those just described
 * - matches any valid Unicode surrogate pair
 * - matches a backslash followed by one or more whitespace characters
 * - matches a backslash followed by one or more letters then whitespace
 * - matches a backslash followed by any BMP character
 * Capturing groups:
 *   [1] regular whitespace
 *   [2] backslash followed by whitespace
 *   [3] anything else, which may include:
 *     [4] left character of \verb*
 *     [5] left character of \verb
 *     [6] backslash followed by word, excluding any trailing whitespace
 * Just because the Lexer matches something doesn't mean it's valid input:
 * If there is no matching function or symbol definition, the Parser will
 * still reject the input.
 */
object Lexer {

  private val spaceRegexString = "[ \\r\\n\\t]"
  private val controlWordRegexString = "\\\\[a-zA-Z@]+"
  private val controlSymbolRegexString = "\\\\[^\\uD800-\\uDFFF]"
  private val controlWordWhitespaceRegexString =
    s"($controlWordRegexString)$spaceRegexString*"
  private val controlSpaceRegexString = "\\\\(\\n|[ \\r\\t]+\\n?)[ \\r\\t]*"
  private val combiningDiacriticalMarkString = "[\\u0300-\\u036f]"

  val combiningDiacriticalMarksEndRegex: Regex =
    new Regex(s"$combiningDiacriticalMarkString+$$")

  private val tokenRegexString: String =
    s"($spaceRegexString+)|" +                        // whitespace
    s"$controlSpaceRegexString|" +                     // \whitespace
    "([!-\\[\\]-\\u2027\\u202A-\\uD7FF\\uF900-\\uFFFF]" + // single codepoint
    s"$combiningDiacriticalMarkString*" +              // ...plus accents
    "|[\\x{10000}-\\x{10FFFF}]" +                      // supplementary char
    s"$combiningDiacriticalMarkString*" +              // ...plus accents
    "|\\\\verb\\*([\\s\\S])[^\\n]*?\\4" +               // \verb*
    "|\\\\verb([^*a-zA-Z])[^\\n]*?\\5" +               // \verb unstarred
    s"|$controlWordWhitespaceRegexString" +            // \macroName + spaces
    s"|$controlSymbolRegexString)"                     // \\, \', etc.

  /** Compiled Pattern for the token regex. Uses backreferences (\4, \5)
   * for \verb matching, which requires java.util.regex (not re2).
   * On all three platforms (JVM, JS, Native), we use java.util.regex.Pattern
   * which supports backreferences.
   */
  val tokenPattern: Pattern = Pattern.compile(tokenRegexString)
}

/** Main Lexer class */
class Lexer(val input: String, val settings: SettingsLike) extends LexerInterface {

  // We provide tokenRegex as a Scala Regex for the LexerInterface contract,
  // but internally we use the java.util.regex.Matcher for stateful matching.
  val tokenRegex: Regex = new Regex(Lexer.tokenRegexString)

  // Category codes. The lexer only supports comment characters (14) for now.
  // MacroExpander additionally distinguishes active (13).
  val catcodes: mutable.Map[String, Int] = mutable.Map(
    "%" -> 14, // comment character
    "~" -> 13  // active character
  )

  // Internal matcher for stateful position tracking
  private val matcher: Matcher = Lexer.tokenPattern.matcher(input)

  // Current position in the input (replaces JS lastIndex)
  private var _pos: Int = 0

  /** Get/set the current lexing position. */
  def pos: Int = _pos
  def pos_=(value: Int): Unit = { _pos = value }

  def setCatcode(char: String, code: Int): Unit = {
    catcodes(char) = code
  }

  /**
   * This function lexes a single token.
   */
  def lex(): Token = boundary {
    val pos = _pos
    if (pos == input.length) {
      break(new Token("EOF", SourceLocation(this, pos, pos)))
    }
    val found = matcher.find(pos)
    if (!found || matcher.start() != pos) {
      // Use codePointAt to correctly handle supplementary characters
      val cp = input.codePointAt(pos)
      val charStr = new String(Character.toChars(cp))
      val charEnd = pos + Character.charCount(cp)
      throw new ParseError(
        s"Unexpected character: '$charStr'",
        new Token(charStr, SourceLocation(this, pos, charEnd)))
    }
    // Update position to end of match
    _pos = matcher.end()

    // Extract captured groups — java.util.regex.Matcher.group() returns
    // Java null when the group did not participate in the match.
    val group6 = matcher.group(6) // controlWord
    val group3 = matcher.group(3) // anything else
    val group2 = matcher.group(2) // backslash whitespace

    // Java interop: Matcher.group() returns null for non-participating groups
    val text: String =
      if (group6 != null) group6  // @nowarn — Java interop null check
      else if (group3 != null) group3
      else if (group2 != null) "\\ "
      else " "

    if (catcodes.getOrElse(text, -1) == 14) { // comment character
      val nlIndex = input.indexOf('\n', _pos)
      if (nlIndex == -1) {
        _pos = input.length // EOF
        settings.reportNonstrict("commentAtEnd",
          "% comment has no terminating newline; LaTeX would " +
          "fail because of commenting the end of math mode (e.g. $)")
      } else {
        _pos = nlIndex + 1
      }
      break(lex())
    }

    new Token(text, SourceLocation(this, pos, _pos))
  }
}
