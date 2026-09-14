/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The Lexer class handles tokenizing the input in various ways.
 *
 * Original source: katex src/Lexer.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package parse

import java.util.regex.{ Matcher, Pattern }

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break
import scala.util.matching.Regex

import lowlevel.Nullable

trait SettingsLike {
  def reportNonstrict(errorCode: String, errorMsg: String): Unit
}

object Lexer {

  // Build regex strings using literal Unicode chars (not \uXXXX escapes)
  // because Scala Native's re2 doesn't support \uXXXX in patterns.
  private val spaceRegexString       = "[ \\r\\n\\t]"
  private val controlWordRegexString = "\\\\[a-zA-Z@]+"
  // \uD800-\uDFFF as literal chars for the negated class
  private val controlSymbolRegexString         = "\\\\[^\uD800-\uDFFF]"
  private val controlWordWhitespaceRegexString =
    s"($controlWordRegexString)$spaceRegexString*"
  private val controlSpaceRegexString = "\\\\(\\n|[ \\r\\t]+\\n?)[ \\r\\t]*"
  // Combining diacritical marks U+0300-U+036F as literal chars
  private val combiningDiacriticalMarkString = "[̀-ͯ]"

  val combiningDiacriticalMarksEndRegex: Regex =
    new Regex(s"$combiningDiacriticalMarkString+$$")

  // \verb matching is handled programmatically in lex() because
  // backreferences are not supported by re2 (Scala Native).
  // Groups: [1]=whitespace, [2]=control-space, [3]=anything-else, [4]=controlWord
  private val tokenRegexString: String =
    s"($spaceRegexString+)|" +
      s"$controlSpaceRegexString|" +
      "([!-\\[\\]-‧‪-퟿豈-￿]" +
      s"$combiningDiacriticalMarkString*" +
      "|" + LexerPlatform.supplementaryCharPattern +
      s"$combiningDiacriticalMarkString*" +
      s"|$controlWordWhitespaceRegexString" +
      s"|$controlSymbolRegexString)"

  val tokenPattern: Pattern = Pattern.compile(tokenRegexString)
}

class Lexer(val input: String, val settings: SettingsLike) extends LexerInterface {

  val tokenRegex: Regex = new Regex(Lexer.tokenRegexString)

  val catcodes: mutable.Map[String, Int] = mutable.Map(
    "%" -> 14,
    "~" -> 13
  )

  private val matcher: Matcher = Lexer.tokenPattern.matcher(input)

  private var _pos: Int = 0

  def pos:               Int  = _pos
  def pos_=(value: Int): Unit = _pos = value

  def setCatcode(char: String, code: Int): Unit =
    catcodes(char) = code

  def lex(): Token = boundary {
    val pos = _pos
    if (pos == input.length) {
      break(new Token("EOF", SourceLocation(this, pos, pos)))
    }

    // Handle \verb before the main regex (backreferences not supported on Native)
    if (input.startsWith("\\verb", pos)) {
      val verbResult = tryLexVerb(pos)
      if (verbResult != null) { // @nowarn — null check for perf
        break(verbResult)
      }
    }

    val found = matcher.find(pos)
    if (!found || matcher.start() != pos) {
      val cp      = input.codePointAt(pos)
      val charStr = new String(Character.toChars(cp))
      val charEnd = pos + Character.charCount(cp)
      throw new ParseError(s"Unexpected character: '$charStr'", new Token(charStr, SourceLocation(this, pos, charEnd)))
    }
    _pos = matcher.end()

    val group4 = matcher.group(4) // controlWord
    val group3 = matcher.group(3) // anything else
    val group2 = matcher.group(2) // backslash whitespace

    val text: String =
      if (group4 != null) group4 // @nowarn — Java interop null check
      else if (group3 != null) group3
      else if (group2 != null) "\\ "
      else " "

    if (catcodes.getOrElse(text, -1) == 14) {
      val nlIndex = input.indexOf('\n', _pos)
      if (nlIndex == -1) {
        _pos = input.length
        settings.reportNonstrict(
          "commentAtEnd",
          "% comment has no terminating newline; LaTeX would " +
            "fail because of commenting the end of math mode (e.g. $)"
        )
      } else {
        _pos = nlIndex + 1
      }
      break(lex())
    }

    new Token(text, SourceLocation(this, pos, _pos))
  }

  private def tryLexVerb(pos: Int): Token | Null = { // @nowarn — returns null for perf
    val afterVerb = pos + 5
    if (afterVerb >= input.length) {
      null // @nowarn
    } else {
      val starred  = input.charAt(afterVerb) == '*'
      val delimPos = if (starred) afterVerb + 1 else afterVerb
      if (delimPos >= input.length) {
        null // @nowarn
      } else {
        val delim = input.charAt(delimPos)
        // Upstream Lexer.ts:60 uses [^*a-zA-Z] — ASCII letters only,
        // NOT Character.isLetter which includes all Unicode letters.
        if (starred || (!isAsciiLetter(delim) && delim != '*')) {
          val contentStart = delimPos + 1
          val endIdx       = input.indexOf(delim, contentStart)
          val nlIdx        = input.indexOf('\n', contentStart)
          if (endIdx == -1 || (nlIdx >= 0 && nlIdx < endIdx)) {
            null // @nowarn
          } else {
            val verbText = input.substring(pos, endIdx + 1)
            _pos = endIdx + 1
            new Token(verbText, SourceLocation(this, pos, _pos))
          }
        } else {
          null // @nowarn
        }
      }
    }
  }

  /** ASCII-only letter test matching upstream Lexer.ts:60 [^*a-zA-Z]. */
  private def isAsciiLetter(c: Char): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
}
