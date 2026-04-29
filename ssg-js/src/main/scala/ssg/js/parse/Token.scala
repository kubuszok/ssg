/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Token type constants, keyword/operator sets, and Unicode identifier helpers
 * for the JavaScript tokenizer.
 *
 * Original source: terser lib/parse.js (lines 177-261, 1087-1128)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: makePredicate → Set[String], characters() → string.toSet
 *   Convention: Scala 3 vals, Set/Map instead of JS predicate functions
 *   Idiom: Character.isLetter / Character.getType for Unicode categories
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/parse.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1055a1d79d4dc147317814a8a456209475792175
 */
package ssg
package js
package parse

object Token {

  // ---- Token type string constants ----

  val Name:         String = "name"
  val Num:          String = "num"
  val BigInt:       String = "big_int"
  val StringType:   String = "string"
  val Regexp:       String = "regexp"
  val Operator:     String = "operator"
  val Keyword:      String = "keyword"
  val Atom:         String = "atom"
  val Punc:         String = "punc"
  val Expand:       String = "expand"
  val Arrow:        String = "arrow"
  val Eof:          String = "eof"
  val TemplateHead: String = "template_head"
  val TemplateCont: String = "template_cont"
  val Comment1:     String = "comment1"
  val Comment2:     String = "comment2"
  val Comment3:     String = "comment3"
  val Comment4:     String = "comment4"
  val Comment5:     String = "comment5"
  val PrivateName:  String = "privatename"

  // ---- Keyword / reserved-word sets ----

  private val keywordsStr: String =
    "break case catch class const continue debugger default delete do else export extends finally for function if in instanceof let new return switch throw try typeof var void while with"

  private val keywordsAtomStr: String = "false null true"

  private val reservedWordsStr: String = "enum import super this " + keywordsAtomStr + " " + keywordsStr

  private val allReservedWordsStr: String =
    "implements interface package private protected public static " + reservedWordsStr

  val KEYWORDS: Set[String] = keywordsStr.split(' ').toSet

  val KEYWORDS_ATOM: Set[String] = keywordsAtomStr.split(' ').toSet

  val RESERVED_WORDS: Set[String] = reservedWordsStr.split(' ').toSet

  val ALL_RESERVED_WORDS: Set[String] = allReservedWordsStr.split(' ').toSet

  val KEYWORDS_BEFORE_EXPRESSION: Set[String] =
    Set("return", "new", "delete", "throw", "else", "case", "yield", "await")

  // ---- Operator / punctuation sets ----

  val OPERATOR_CHARS: Set[Char] = "+-*&%=<>!?|~^".toSet

  val OPERATORS: Set[String] = Set(
    "in",
    "instanceof",
    "typeof",
    "new",
    "void",
    "delete",
    "++",
    "--",
    "+",
    "-",
    "!",
    "~",
    "&",
    "|",
    "^",
    "*",
    "**",
    "/",
    "%",
    ">>",
    "<<",
    ">>>",
    "<",
    ">",
    "<=",
    ">=",
    "==",
    "===",
    "!=",
    "!==",
    "?",
    "=",
    "+=",
    "-=",
    "||=",
    "&&=",
    "??=",
    "/=",
    "*=",
    "**=",
    "%=",
    ">>=",
    "<<=",
    ">>>=",
    "|=",
    "^=",
    "&=",
    "&&",
    "??",
    "||"
  )

  val ASSIGNMENT: Set[String] = Set(
    "=",
    "+=",
    "-=",
    "??=",
    "&&=",
    "||=",
    "/=",
    "*=",
    "**=",
    "%=",
    ">>=",
    "<<=",
    ">>>=",
    "|=",
    "^=",
    "&="
  )

  val LOGICAL_ASSIGNMENT: Set[String] = Set("??=", "&&=", "||=")

  val PRECEDENCE: Map[String, Int] = Map(
    "||" -> 1,
    "??" -> 2,
    "&&" -> 3,
    "|" -> 4,
    "^" -> 5,
    "&" -> 6,
    "==" -> 7,
    "===" -> 7,
    "!=" -> 7,
    "!==" -> 7,
    "<" -> 8,
    ">" -> 8,
    "<=" -> 8,
    ">=" -> 8,
    "in" -> 8,
    "instanceof" -> 8,
    ">>" -> 9,
    "<<" -> 9,
    ">>>" -> 9,
    "+" -> 10,
    "-" -> 10,
    "*" -> 11,
    "/" -> 11,
    "%" -> 11,
    "**" -> 12
  )

  val UNARY_PREFIX: Set[String] = Set("typeof", "void", "delete", "--", "++", "!", "~", "-", "+")

  val UNARY_POSTFIX: Set[String] = Set("--", "++")

  val PUNC_BEFORE_EXPRESSION: Set[Char] = "[{(,;:".toSet

  val PUNC_AFTER_EXPRESSION: Set[Char] = ";]),:".toSet

  val PUNC_CHARS: Set[Char] = "[]{}(),;:".toSet

  val ATOMIC_START_TOKEN: Set[String] = Set("atom", "num", "big_int", "string", "regexp", "name")

  // ---- Whitespace / newline character sets ----

  val WHITESPACE_CHARS: Set[Char] = Set(
    ' ', '\u00a0', '\n', '\r', '\t', '\f', '\u000b', '\u200b', '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005', '\u2006', '\u2007', '\u2008', '\u2009', '\u200a', '\u2028', '\u2029',
    '\u202f', '\u205f', '\u3000', '\ufeff'
  )

  val NEWLINE_CHARS: Set[Char] = Set('\n', '\r', '\u2028', '\u2029')

  // ---- Number format checks ----
  // Manual checks instead of regex with embedded flags — Scala.js does not
  // support (?i) embedded flag expressions in regex patterns.

  private val reDecNumber = "^\\d*\\.?\\d*(?:[eE][+-]?\\d*(?:\\d\\.?|\\.?\\d)\\d*)?$".r

  def isHexNumber(s: String): Boolean =
    s.length > 2 &&
      s.charAt(0) == '0' &&
      (s.charAt(1) == 'x' || s.charAt(1) == 'X') &&
      s.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == 'x' || c == 'X')

  def isOctNumber(s: String): Boolean =
    s.length > 1 &&
      s.charAt(0) == '0' &&
      s.charAt(1) >= '0' && s.charAt(1) <= '7' &&
      s.forall(c => c >= '0' && c <= '7')

  def isEs6OctNumber(s: String): Boolean =
    s.length > 2 &&
      s.charAt(0) == '0' &&
      (s.charAt(1) == 'o' || s.charAt(1) == 'O') && {
        var i  = 2
        var ok = true
        while (i < s.length && ok) {
          val c = s.charAt(i)
          if (c < '0' || c > '7') ok = false
          i += 1
        }
        ok
      }

  def isBinNumber(s: String): Boolean =
    s.length > 2 &&
      s.charAt(0) == '0' &&
      (s.charAt(1) == 'b' || s.charAt(1) == 'B') && {
        var i  = 2
        var ok = true
        while (i < s.length && ok) {
          val c = s.charAt(i)
          if (c != '0' && c != '1') ok = false
          i += 1
        }
        ok
      }

  def isDecNumber(s: String): Boolean = reDecNumber.findFirstIn(s).isDefined

  def isBigInt(s: String): Boolean =
    if (s.length < 2 || s.charAt(s.length - 1) != 'n') {
      false
    } else {
      val body = s.substring(0, s.length - 1)
      if (body.length > 2 && body.charAt(0) == '0') {
        val c1 = body.charAt(1)
        if (c1 == 'x' || c1 == 'X') {
          body.substring(2).forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
        } else if (c1 == 'o' || c1 == 'O') {
          body.substring(2).forall(c => c >= '0' && c <= '7')
        } else if (c1 == 'b' || c1 == 'B') {
          body.substring(2).forall(c => c == '0' || c == '1')
        } else {
          body.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
        }
      } else {
        body.nonEmpty && body.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
      }
    }

  /** Parse a JavaScript numeric literal to a Double, mirroring terser's parse_js_number. */
  def parseJsNumber(num: String, allowE: Boolean = true): Double =
    if (!allowE && num.contains("e")) {
      Double.NaN
    } else if (isHexNumber(num)) {
      java.lang.Long.parseLong(num.substring(2), 16).toDouble
    } else if (isOctNumber(num)) {
      java.lang.Long.parseLong(num.substring(1), 8).toDouble
    } else if (isEs6OctNumber(num)) {
      java.lang.Long.parseLong(num.substring(2), 8).toDouble
    } else if (isBinNumber(num)) {
      java.lang.Long.parseLong(num.substring(2), 2).toDouble
    } else if (isDecNumber(num)) {
      try num.toDouble
      catch { case _: NumberFormatException => Double.NaN }
    } else {
      try {
        val v = num.toDouble
        if (v.toString == num) v else Double.NaN
      } catch { case _: NumberFormatException => Double.NaN }
    }

  // ---- Unicode identifier helpers ----

  /** Tests whether a code point can start a JavaScript identifier (Unicode ID_Start or _ or $ or \). */
  def isIdentifierStart(ch: String): Boolean =
    if (ch.isEmpty) {
      false
    } else {
      val cp = ch.codePointAt(0)
      isIdentifierStartCodePoint(cp)
    }

  def isIdentifierStartCodePoint(cp: Int): Boolean =
    if (cp == '$' || cp == '_' || cp == '\\') {
      true
    } else {
      val ct = Character.getType(cp)
      ct == Character.UPPERCASE_LETTER ||
      ct == Character.LOWERCASE_LETTER ||
      ct == Character.TITLECASE_LETTER ||
      ct == Character.MODIFIER_LETTER ||
      ct == Character.OTHER_LETTER ||
      ct == Character.LETTER_NUMBER
    }

  /** Tests whether a code point can continue a JavaScript identifier (Unicode ID_Continue or $ or \u200c/\u200d). */
  def isIdentifierChar(ch: String): Boolean =
    if (ch.isEmpty) {
      false
    } else {
      val cp = ch.codePointAt(0)
      isIdentifierCharCodePoint(cp)
    }

  def isIdentifierCharCodePoint(cp: Int): Boolean =
    if (cp == '$' || cp == '_' || cp == '\\' || cp == 0x200c || cp == 0x200d) {
      true
    } else {
      val ct = Character.getType(cp)
      ct == Character.UPPERCASE_LETTER ||
      ct == Character.LOWERCASE_LETTER ||
      ct == Character.TITLECASE_LETTER ||
      ct == Character.MODIFIER_LETTER ||
      ct == Character.OTHER_LETTER ||
      ct == Character.LETTER_NUMBER ||
      ct == Character.NON_SPACING_MARK ||
      ct == Character.COMBINING_SPACING_MARK ||
      ct == Character.DECIMAL_DIGIT_NUMBER ||
      ct == Character.CONNECTOR_PUNCTUATION
    }

  /** Check if a string is a valid basic identifier (ASCII letters, digits, _, $). */
  private val basicIdentPattern = "^[a-zA-Z_$][a-zA-Z0-9_$]*$".r

  def isBasicIdentifierString(str: String): Boolean =
    basicIdentPattern.findFirstIn(str).isDefined

  def isIdentifierString(str: String, allowSurrogates: Boolean = false): Boolean =
    if (isBasicIdentifierString(str)) {
      true
    } else {
      if (!allowSurrogates && str.exists(c => c >= '\ud800' && c <= '\udfff')) {
        false
      } else if (str.isEmpty) {
        false
      } else {
        val firstCp = str.codePointAt(0)
        if (!isIdentifierStartCodePoint(firstCp)) {
          false
        } else {
          val rest = str.substring(Character.charCount(firstCp))
          rest.isEmpty || rest.codePoints().allMatch(cp => isIdentifierCharCodePoint(cp))
        }
      }
    }

  // ---- Surrogate pair helpers ----

  def isSurrogatePairHead(code: Int): Boolean = code >= 0xd800 && code <= 0xdbff

  def isSurrogatePairTail(code: Int): Boolean = code >= 0xdc00 && code <= 0xdfff

  def isDigit(code: Int): Boolean = code >= 48 && code <= 57

  /** Get the full character (handling surrogate pairs) at the given position. Returns an empty string if pos is past end of string.
    */
  def getFullChar(text: String, pos: Int): String =
    if (pos < 0 || pos >= text.length) {
      ""
    } else {
      val cp = text.codePointAt(pos)
      if (Character.charCount(cp) > 1 && pos + 1 < text.length) {
        text.substring(pos, pos + 2)
      } else {
        text.substring(pos, pos + 1)
      }
    }

  /** Compute the "full char length" of a string (number of code points), accounting for surrogate pairs.
    */
  def getFullCharLength(text: String): Int =
    text.codePointCount(0, text.length)

  /** Convert a code point to its string representation (handling supplementary planes). */
  def fromCharCode(code: Int): String =
    new String(Character.toChars(code))
}
