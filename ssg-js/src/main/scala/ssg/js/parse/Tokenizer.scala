/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JavaScript tokenizer — converts source text into a stream of AstToken objects.
 *
 * Original source: terser lib/parse.js (tokenizer function, lines 260-1083)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: tokenizer() closure → Tokenizer class, snake_case → camelCase internals
 *   Convention: Mutable state as class vars, boundary/break for early return
 *   Idiom: scala.util.boundary replaces JS return, ArrayBuffer replaces JS []
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

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.AstToken

/** Error thrown when the tokenizer encounters invalid JavaScript syntax. */
final case class JsParseError(
  message:  String,
  filename: String,
  line:     Int,
  col:      Int,
  pos:      Int
) extends Exception(s"SyntaxError: $message ($filename:$line:$col)")

/** JavaScript tokenizer that converts source text into a stream of [[AstToken]] objects.
  *
  * Port of the `tokenizer` closure from terser's parse.js. The JS closure's mutable `S` state object is represented as mutable fields on this class.
  *
  * @param text
  *   the JavaScript source code
  * @param filename
  *   source filename for error messages
  * @param html5Comments
  *   whether to recognize `<!--` and `-->` as comment starts
  * @param shebang
  *   whether to treat `#!` on line 1 as a comment
  */
class Tokenizer(
  val text:          String,
  val filename:      String = "",
  val html5Comments: Boolean = true,
  val shebang:       Boolean = true
) {

  import Token._

  // ---- Mutable tokenizer state (mirrors JS `S` object) ----

  var pos:            Int                                       = 0
  var tokpos:         Int                                       = 0
  var line:           Int                                       = 1
  var tokline:        Int                                       = 0
  var col:            Int                                       = 0
  var tokcol:         Int                                       = 0
  var newlineBefore:  Boolean                                   = false
  var regexAllowed:   Boolean                                   = false
  var braceCounter:   Int                                       = 0
  val templateBraces: ArrayBuffer[Int]                          = ArrayBuffer.empty
  var commentsBefore: ArrayBuffer[AstToken]                     = ArrayBuffer.empty
  val directives:     scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map.empty
  val directiveStack: ArrayBuffer[ArrayBuffer[String]]          = ArrayBuffer.empty

  /** Latest raw token text — used for numbers and template strings. */
  var latestRaw: String = ""

  /** Raw template string data, keyed by token. */
  val templateRaws: scala.collection.mutable.Map[AstToken, String] =
    scala.collection.mutable.Map.empty

  private var prevWasDot:    Boolean  = false
  private var previousToken: AstToken = null.asInstanceOf[AstToken] // @nowarn — interop sentinel

  // ---- Sentinel for EOF in with_eof_error pattern ----

  private class EofException extends Exception

  private val ExEof: EofException = new EofException

  // ---- Character-level operations ----

  /** Return the full character (handling surrogate pairs) at the current position without advancing. */
  def peek(): String = getFullChar(text, pos)

  /** Used because parsing ?. involves a lookahead for a digit. */
  def isOptionChainOp(): Boolean =
    if (pos + 1 >= text.length) {
      false
    } else {
      val mustBeDot = text.charAt(pos + 1) == '.'
      if (!mustBeDot) {
        false
      } else {
        if (pos + 2 >= text.length) {
          true
        } else {
          val cannotBeDigit = text.charAt(pos + 2).toInt
          cannotBeDigit < 48 || cannotBeDigit > 57
        }
      }
    }

  /** Advance position and return the current character. Handles newline normalization (\r\n → \n) and column tracking.
    */
  def next(signalEof: Boolean = false, inString: Boolean = false): String = {
    val ch = getFullChar(text, pos)
    pos += 1
    if (signalEof && ch.isEmpty) {
      throw ExEof
    }
    if (ch.nonEmpty && NEWLINE_CHARS.contains(ch.charAt(0))) {
      newlineBefore = newlineBefore || !inString
      line += 1
      col = 0
      if (ch == "\r" && peek() == "\n") {
        // treat a \r\n sequence as a single \n
        pos += 1
        return "\n" // boundary/break not needed — single return in simple conditional
      }
    } else {
      if (ch.length > 1) {
        pos += 1
        col += 1
      }
      col += 1
    }
    ch
  }

  /** Advance position by `i` characters. */
  def forward(i: Int): Unit = {
    var n = i
    while (n > 0) {
      next()
      n -= 1
    }
  }

  /** Check if text at current position starts with `str`. */
  def lookingAt(str: String): Boolean =
    text.regionMatches(pos, str, 0, str.length)

  /** Find the position of the next newline character starting from current position, or -1. */
  def findEol(): Int = {
    var i = pos
    val n = text.length
    while (i < n) {
      if (NEWLINE_CHARS.contains(text.charAt(i))) {
        return i // boundary/break not needed — simple search loop
      }
      i += 1
    }
    -1
  }

  /** Find the position of `what` in text starting from current position. */
  def find(what: String, signalEof: Boolean = false): Int = {
    val result = text.indexOf(what, pos)
    if (signalEof && result == -1) throw ExEof
    result
  }

  /** Record the start position of a new token. */
  def startToken(): Unit = {
    tokline = line
    tokcol = col
    tokpos = pos
  }

  // ---- Token creation ----

  /** Create an [[AstToken]] and update regex_allowed state. Mirrors the JS `token(type, value, is_comment)` inner function.
    */
  def token(tokenType: String, value: String, isComment: Boolean = false): AstToken = {
    regexAllowed = (tokenType == Token.Operator && !UNARY_POSTFIX.contains(value)) ||
      (tokenType == Token.Keyword && KEYWORDS_BEFORE_EXPRESSION.contains(value)) ||
      (tokenType == Token.Punc && PUNC_BEFORE_EXPRESSION.contains(value.headOption.getOrElse(' '))) ||
      (tokenType == Token.Arrow)

    if (tokenType == Token.Punc && (value == "." || value == "?.")) {
      prevWasDot = true
    } else if (!isComment) {
      prevWasDot = false
    }

    val tokLine = tokline
    val tokCol  = tokcol
    val tokPos  = tokpos
    val nlb     = newlineBefore

    var commBefore: List[AstToken] = Nil
    var commAfter:  List[AstToken] = Nil

    if (!isComment) {
      commBefore = commentsBefore.toList
      commentsBefore = ArrayBuffer.empty
      commAfter = Nil
    }
    newlineBefore = false

    val tok = AstToken(
      tokenType = tokenType,
      value = value,
      line = tokLine,
      col = tokCol,
      pos = tokPos,
      flags = if (nlb) AstToken.FlagNlb else 0,
      commentsBefore = commBefore,
      commentsAfter = commAfter,
      file = filename
    )

    if (!isComment) previousToken = tok
    tok
  }

  // ---- Whitespace ----

  def skipWhitespace(): Unit =
    while ({
      val ch = peek()
      ch.nonEmpty && WHITESPACE_CHARS.contains(ch.charAt(0))
    })
      next()

  // ---- Lookahead helpers ----

  /** Peek ahead past whitespace and comments to find the next token start or newline. */
  final case class PeekResult(char: String, pos: Int)

  def peekNextTokenStartOrNewline(): PeekResult = {
    var p                  = pos
    var inMultilineComment = false
    while (p < text.length) {
      val ch = getFullChar(text, p)
      if (ch.nonEmpty && NEWLINE_CHARS.contains(ch.charAt(0))) {
        return PeekResult(ch, p) // boundary/break not needed — simple return
      } else if (inMultilineComment) {
        if (ch == "*" && getFullChar(text, p + 1) == "/") {
          p += 2
          inMultilineComment = false
        } else {
          p += 1
        }
      } else if (ch.isEmpty || !WHITESPACE_CHARS.contains(ch.charAt(0))) {
        if (ch == "/") {
          val nextCh = getFullChar(text, p + 1)
          if (nextCh == "/") {
            // skip to end of line
            var eol = p + 2
            while (eol < text.length && !NEWLINE_CHARS.contains(text.charAt(eol)))
              eol += 1
            if (eol < text.length) {
              return PeekResult(getFullChar(text, eol), eol)
            } else {
              return PeekResult("", eol)
            }
          } else if (nextCh == "*") {
            inMultilineComment = true
            p += 2
          } else {
            return PeekResult(ch, p)
          }
        } else {
          return PeekResult(ch, p)
        }
      } else {
        p += 1
      }
    }
    PeekResult("", p)
  }

  /** Check if `ch` at `chPos` starts a binding identifier (not `in`/`instanceof` keyword). */
  def chStartsBindingIdentifier(ch: String, chPos: Int): Boolean =
    if (ch == "\\") {
      true
    } else if (isIdentifierStart(ch)) {
      // Check if this is "in" or "instanceof" keyword
      if (chPos + 2 <= text.length && text.regionMatches(chPos, "in", 0, 2)) {
        val afterIn = chPos + 2
        if (afterIn < text.length && text.regionMatches(chPos, "instanceof", 0, 10)) {
          val afterInstanceof = chPos + 10
          if (afterInstanceof >= text.length) {
            false
          } else {
            val after = getFullChar(text, afterInstanceof)
            isIdentifierChar(after) || after == "\\"
          }
        } else if (afterIn >= text.length) {
          false
        } else {
          val after = getFullChar(text, afterIn)
          if (!isIdentifierChar(after) && after != "\\") {
            false
          } else {
            true
          }
        }
      } else {
        true
      }
    } else {
      false
    }

  /** Read characters while predicate holds, return the accumulated string. */
  def readWhile(pred: (String, Int) => Boolean): String = {
    val sb = new StringBuilder
    var i  = 0
    var ch = peek()
    while (ch.nonEmpty && pred(ch, i)) {
      sb.append(next())
      i += 1
      ch = peek()
    }
    sb.toString
  }

  // ---- Error reporting ----

  def parseError(err: String): Nothing =
    throw JsParseError(err, filename, tokline, tokcol, tokpos)

  // ---- Number reading ----

  def readNum(prefix: String = ""): AstToken = {
    var hasE             = false
    var afterE           = false
    var hasX             = false
    var hasDot           = prefix == "."
    var isBigIntLit      = false
    var numericSeparator = false

    val num0 = readWhile { (ch, i) =>
      if (isBigIntLit) {
        false
      } else {
        val code = ch.charAt(0).toInt
        code match {
          case 95 => // _
            numericSeparator = true
            true
          case 98 | 66 => // bB
            hasX = true // Can occur in hex sequence, don't return false yet
            true
          case 111 | 79 | 120 | 88 => // oO xX
            if (hasX) false
            else { hasX = true; true }
          case 101 | 69 => // eE
            if (hasX) true
            else if (hasE) false
            else { hasE = true; afterE = true; true }
          case 45 => // -
            afterE || (i == 0 && prefix.isEmpty)
          case 43 => // +
            afterE
          case 46 => // .
            afterE = false
            if (!hasDot && !hasX && !hasE) { hasDot = true; true }
            else false
          case 110 => // n
            isBigIntLit = true
            true
          case _ =>
            afterE = false
            (code >= 48 && code <= 57) || // 0-9
            (code >= 97 && code <= 102) || // a-f
            (code >= 65 && code <= 70) // A-F
        }
      }
    }
    val num = if (prefix.nonEmpty) prefix + num0 else num0

    latestRaw = num

    if (isOctNumber(num) && hasDirective("use strict")) {
      parseError("Legacy octal literals are not allowed in strict mode")
    }
    if (numericSeparator) {
      if (num.endsWith("_")) {
        parseError("Numeric separators are not allowed at the end of numeric literals")
      } else if (num.contains("__")) {
        parseError("Only one underscore is allowed as numeric separator")
      }
    }
    val numClean = if (numericSeparator) num.replace("_", "") else num
    if (isBigIntLit) {
      val withoutN = numClean.substring(0, numClean.length - 1)
      val allowE   = isHexNumber(withoutN)
      val valid    = parseJsNumber(withoutN, allowE)
      if (!hasDot && Token.isBigInt(numClean) && !valid.isNaN) {
        token(Token.BigInt, withoutN)
      } else {
        parseError("Invalid or unexpected token")
      }
    } else {
      val valid = parseJsNumber(numClean)
      if (!valid.isNaN) {
        token(Token.Num, numClean)
      } else {
        parseError("Invalid syntax: " + numClean)
      }
    }
  }

  // ---- Escape sequences ----

  private def isOctal(ch: String): Boolean =
    ch.nonEmpty && ch.charAt(0) >= '0' && ch.charAt(0) <= '7'

  def readEscapedChar(
    inString:       Boolean,
    strictHex:      Boolean,
    templateString: Boolean = false
  ): String = {
    val ch = next(signalEof = true, inString = inString)
    ch.charAt(0).toInt match {
      case 110 => "\n" // n
      case 114 => "\r" // r
      case 116 => "\t" // t
      case 98  => "\b" // b
      case 118 => "\u000b" // v
      case 102 => "\f" // f
      case 120 => // x
        Character.toChars(hexBytes(2, strictHex)).mkString
      case 117 => // u
        if (peek() == "{") {
          next(signalEof = true)
          if (peek() == "}") {
            parseError("Expecting hex-character between {}")
          }
          while (peek() == "0") next(signalEof = true) // No significance
          val length = find("}", signalEof = true) - pos
          // Avoid 32 bit integer overflow
          // We know first character isn't 0 and thus out of range anyway
          val result = if (length > 6) -1 else hexBytes(length, strictHex)
          if (length > 6 || result > 0x10ffff) {
            parseError("Unicode reference out of bounds")
          }
          next(signalEof = true) // consume closing }
          fromCharCode(result)
        } else {
          Character.toChars(hexBytes(4, strictHex)).mkString
        }
      case 10 => "" // newline
      case 13 => // \r
        if (peek() == "\n") { // DOS newline
          next(signalEof = true, inString = inString)
        }
        ""
      case _ =>
        if (isOctal(ch)) {
          if (templateString && strictHex) {
            val representsNullCharacter = ch == "0" && !isOctal(peek())
            if (!representsNullCharacter) {
              parseError("Octal escape sequences are not allowed in template strings")
            }
          }
          readOctalEscapeSequence(ch, strictHex)
        } else {
          ch
        }
    }
  }

  private def readOctalEscapeSequence(ch0: String, strictOctal: Boolean): String = {
    var ch = ch0
    // Read
    var p = peek()
    if (p.nonEmpty && p.charAt(0) >= '0' && p.charAt(0) <= '7') {
      ch += next(signalEof = true)
      if (ch.charAt(0) <= '3') {
        p = peek()
        if (p.nonEmpty && p.charAt(0) >= '0' && p.charAt(0) <= '7') {
          ch += next(signalEof = true)
        }
      }
    }
    // Parse
    if (ch == "0") {
      "\u0000"
    } else {
      if (ch.nonEmpty && hasDirective("use strict") && strictOctal) {
        parseError("Legacy octal escape sequences are not allowed in strict mode")
      }
      Character.toChars(Integer.parseInt(ch, 8)).mkString
    }
  }

  private def hexBytes(n: Int, strictHex: Boolean): Int = {
    var num       = ""
    var remaining = n
    while (remaining > 0) {
      if (!strictHex) {
        val pk = peek()
        if (pk.isEmpty || !isHexChar(pk.charAt(0))) {
          val parsed =
            try Integer.parseInt(num, 16)
            catch { case _: NumberFormatException => -1 }
          if (num.isEmpty) return 0 // "" case from original
          return parsed
        }
      }
      val digit = next(signalEof = true)
      if (!isHexChar(digit.charAt(0))) {
        parseError("Invalid hex-character pattern in string")
      }
      num += digit
      remaining -= 1
    }
    Integer.parseInt(num, 16)
  }

  private def isHexChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  // ---- String reading ----

  def readString(): AstToken =
    withEofError("Unterminated string constant") {
      val startPos = pos
      val quote    = next()
      val ret      = new StringBuilder
      var done     = false
      while (!done) {
        var ch = next(signalEof = true, inString = true)
        if (ch == "\\") {
          ch = readEscapedChar(inString = true, strictHex = true)
        } else if (ch == "\r" || ch == "\n") {
          parseError("Unterminated string constant")
        } else if (ch == quote) {
          done = true
          ch = "" // don't append
        }
        if (ch.nonEmpty) ret.append(ch)
      }
      val tok = token(Token.StringType, ret.toString)
      latestRaw = text.substring(startPos, pos)
      tok.quote_=(quote)
      tok
    }

  // ---- Template literal reading ----

  def readTemplateCharacters(begin: Boolean): AstToken =
    withEofError("Unterminated template") {
      if (begin) {
        templateBraces += braceCounter
      }
      val content = new StringBuilder
      val raw     = new StringBuilder
      next(signalEof = true, inString = true) // consume opening ` or }
      var done = false
      var interpolationTok: AstToken = null.asInstanceOf[AstToken] // @nowarn — set in loop
      while (!done && interpolationTok == null) {
        var ch = next(signalEof = true, inString = true)
        if (ch == "`") {
          done = true
        } else {
          if (ch == "\r") {
            if (peek() == "\n") pos += 1
            ch = "\n"
          } else if (ch == "$" && peek() == "{") {
            next(signalEof = true, inString = true)
            braceCounter += 1
            interpolationTok = token(if (begin) Token.TemplateHead else Token.TemplateCont, content.toString)
            templateRaws(interpolationTok) = raw.toString
            interpolationTok.templateEnd_=(false)
          }
          if (interpolationTok == null) {
            raw.append(ch)
            if (ch == "\\") {
              val tmp       = pos
              val prevIsTag = previousToken != null && (
                previousToken.tokenType == Token.Name ||
                  (previousToken.tokenType == Token.Punc &&
                    (previousToken.value == ")" || previousToken.value == "]"))
              )
              ch = readEscapedChar(inString = true, strictHex = !prevIsTag, templateString = true)
              raw.append(text.substring(tmp, pos))
            }
            content.append(ch)
          }
        }
      }
      if (interpolationTok != null) {
        interpolationTok
      } else {
        templateBraces.remove(templateBraces.length - 1)
        val tok = token(if (begin) Token.TemplateHead else Token.TemplateCont, content.toString)
        templateRaws(tok) = raw.toString
        tok.templateEnd_=(true)
        tok
      }
    }

  // ---- Comment reading ----

  def skipLineComment(commentType: String): Unit = {
    val savedRegexAllowed = regexAllowed
    val i                 = findEol()
    val ret               = if (i == -1) {
      val s = text.substring(pos)
      pos = text.length
      s
    } else {
      val s = text.substring(pos, i)
      pos = i
      s
    }
    col = tokcol + (pos - tokpos)
    commentsBefore += token(commentType, ret, isComment = true)
    regexAllowed = savedRegexAllowed
  }

  def skipMultilineComment(): Unit =
    withEofError("Unterminated multiline comment") {
      val savedRegexAllowed = regexAllowed
      val i                 = find("*/", signalEof = true)
      var commentText       = text.substring(pos, i)
      // normalize newlines
      commentText = commentText.replace("\r\n", "\n").replace("\r", "\n").replace("\u2028", "\n").replace("\u2029", "\n")
      // update stream position
      forward(getFullCharLength(commentText) + 2)
      commentsBefore += token(Token.Comment2, commentText, isComment = true)
      newlineBefore = newlineBefore || commentText.contains("\n")
      regexAllowed = savedRegexAllowed
    }

  // ---- Name / identifier reading ----

  def readName(): String = {
    // Fast path: ASCII-only identifiers
    val start = pos
    var end   = start - 1
    var ch    = 'c' // dummy init
    var cont  = true
    while (cont) {
      end += 1
      if (end < text.length) {
        ch = text.charAt(end)
        if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
          cont = false
        }
      } else {
        cont = false
        ch = 0.toChar
      }
    }

    // 0x7F is very rare in actual code, so we compare it to '~' (0x7E)
    if (end > start + 1 && ch != 0.toChar && ch != '\\' && !isIdentifierChar(ch.toString) && ch <= '~') {
      pos += end - start
      col += end - start
      text.substring(start, pos)
    } else {
      readNameHard()
    }
  }

  private def readNameHard(): String =
    withEofError("Unterminated identifier name") {
      boundary[String] {
        val name    = new StringBuilder
        var escaped = false

        def readEscapedIdentifierChar(): String = {
          escaped = true
          next()
          if (peek() != "u") {
            parseError("Expecting UnicodeEscapeSequence -- uXXXX or u{XXXX}")
          }
          readEscapedChar(inString = false, strictHex = true)
        }

        // Read first character (ID_Start)
        var ch = peek()
        if (ch == "\\") {
          ch = readEscapedIdentifierChar()
          if (!isIdentifierStart(ch)) {
            parseError("First identifier char is an invalid identifier char")
          }
        } else if (isIdentifierStart(ch)) {
          next()
        } else {
          break("")
        }
        name.append(ch)

        // Read ID_Continue
        ch = peek()
        while (ch.nonEmpty) {
          if (ch == "\\") {
            ch = readEscapedIdentifierChar()
            if (!isIdentifierChar(ch)) {
              parseError("Invalid escaped identifier char")
            }
          } else {
            if (!isIdentifierChar(ch)) {
              ch = "" // force loop exit
            } else {
              next()
            }
          }
          if (ch.nonEmpty) {
            name.append(ch)
            ch = peek()
          }
        }
        val nameStr = name.toString
        if (RESERVED_WORDS.contains(nameStr) && escaped) {
          parseError("Escaped characters are not allowed in keywords")
        }
        nameStr
      } // boundary
    }

  // ---- Regexp reading ----

  def readRegexp(source0: String): AstToken =
    withEofError("Unterminated regular expression") {
      var source        = source0
      var prevBackslash = false
      var inClass       = false
      var done          = false
      while (!done) {
        val ch = next(signalEof = true)
        if (ch.nonEmpty && NEWLINE_CHARS.contains(ch.charAt(0))) {
          parseError("Unexpected line terminator")
        } else if (prevBackslash) {
          if (ch.length == 1 && ch.charAt(0) >= '\u0000' && ch.charAt(0) <= '\u007f') {
            source += "\\" + ch
          } else {
            // Remove the useless slash before the escape, but only for characters
            // that won't be added to regexp syntax
            source += ch
          }
          prevBackslash = false
        } else if (ch == "[") {
          inClass = true
          source += ch
        } else if (ch == "]" && inClass) {
          inClass = false
          source += ch
        } else if (ch == "/" && !inClass) {
          done = true
        } else if (ch == "\\") {
          prevBackslash = true
        } else {
          source += ch
        }
      }
      val flags = readName()
      token(Token.Regexp, "/" + source + "/" + flags)
    }

  // ---- Operator reading ----

  def readOperator(prefix: String = ""): AstToken = {
    def grow(op: String): String = {
      val pk = peek()
      if (pk.isEmpty) {
        op
      } else {
        val bigger = op + pk
        if (OPERATORS.contains(bigger)) {
          next()
          grow(bigger)
        } else {
          op
        }
      }
    }
    val initial = if (prefix.nonEmpty) prefix else next()
    token(Token.Operator, grow(initial))
  }

  // ---- Slash handling (comment vs regex vs division) ----

  /** Returns `true` if a comment was consumed (caller should continue the loop). */
  def handleSlash(): (AstToken, Boolean) = {
    next()
    peek() match {
      case "/" =>
        next()
        skipLineComment(Token.Comment1)
        (null.asInstanceOf[AstToken], true) // @nowarn — sentinel, was comment
      case "*" =>
        next()
        skipMultilineComment()
        (null.asInstanceOf[AstToken], true) // @nowarn — sentinel, was comment
      case _ =>
        if (regexAllowed) (readRegexp(""), false)
        else (readOperator("/"), false)
    }
  }

  // ---- Arrow / equals handling ----

  def handleEqSign(): AstToken = {
    next()
    if (peek() == ">") {
      next()
      token(Token.Arrow, "=>")
    } else {
      readOperator("=")
    }
  }

  // ---- Dot handling ----

  def handleDot(): AstToken = {
    next()
    if (peek().nonEmpty && isDigit(peek().charAt(0).toInt)) {
      readNum(".")
    } else if (peek() == ".") {
      next() // Consume second dot
      next() // Consume third dot
      token(Token.Expand, "...")
    } else {
      token(Token.Punc, ".")
    }
  }

  // ---- Word / keyword reading ----

  def readWord(): AstToken = {
    val word = readName()
    if (prevWasDot) {
      token(Token.Name, word)
    } else if (KEYWORDS_ATOM.contains(word)) {
      token(Token.Atom, word)
    } else if (!KEYWORDS.contains(word)) {
      token(Token.Name, word)
    } else if (OPERATORS.contains(word)) {
      token(Token.Operator, word)
    } else {
      token(Token.Keyword, word)
    }
  }

  /** Read a private field name (#identifier). */
  def readPrivateWord(): AstToken = {
    next()
    token(Token.PrivateName, readName())
  }

  // ---- EOF error wrapper ----

  /** Wraps a block so that an EOF exception is turned into a parse error with the given message. Mirrors JS `with_eof_error`.
    */
  private def withEofError[A](eofError: String)(block: => A): A =
    try
      block
    catch {
      case _: EofException => parseError(eofError)
    }

  // ---- Main token dispatch ----

  /** Read and return the next token from the input. This is the main entry point, replacing the JS `next_token` function.
    *
    * @param forceRegexp
    *   if non-null, force reading a regexp with this prefix
    */
  def nextToken(forceRegexp: String = null.asInstanceOf[String]): AstToken = { // @nowarn — null sentinel for optional param
    if (forceRegexp != null) {
      return readRegexp(forceRegexp)
    }
    if (shebang && pos == 0 && lookingAt("#!")) {
      startToken()
      forward(2)
      skipLineComment(Token.Comment5)
    }
    var cont = true
    while (cont) {
      skipWhitespace()
      startToken()
      if (html5Comments) {
        if (lookingAt("<!--")) {
          forward(4)
          skipLineComment(Token.Comment3)
        } else if (lookingAt("-->") && newlineBefore) {
          forward(3)
          skipLineComment(Token.Comment4)
        } else {
          cont = false
        }
        // After handling HTML comments, check if we consumed something and loop
        if (cont && !lookingAt("<!--") && !(lookingAt("-->") && newlineBefore)) {
          cont = false
        }
      } else {
        cont = false
      }
    }

    // At this point we may have consumed HTML comments. Now actually dispatch on the character.
    // Re-enter the main loop to handle the dispatch properly.
    boundary[AstToken] {
      while (true) {
        skipWhitespace()
        startToken()
        if (html5Comments) {
          if (lookingAt("<!--")) {
            forward(4)
            skipLineComment(Token.Comment3)
            // continue the while loop — a comment was consumed
          } else if (lookingAt("-->") && newlineBefore) {
            forward(3)
            skipLineComment(Token.Comment4)
            // continue
          } else {
            // fall through to dispatch
            val tok = dispatchToken()
            if (tok != null) break(tok)
          }
        } else {
          val tok = dispatchToken()
          if (tok != null) break(tok)
        }
      }
      // unreachable, but satisfies type checker
      token(Token.Eof, "")
    }
  }

  /** Dispatch on the current character to read the appropriate token. Returns null if a comment was consumed (caller should loop).
    */
  private def dispatchToken(): AstToken = {
    val ch = peek()
    if (ch.isEmpty) {
      return token(Token.Eof, "")
    }
    val code = ch.charAt(0).toInt
    code match {
      case 34 | 39 => // " or '
        readString()
      case 46 => // .
        handleDot()
      case 47 => // /
        val (tok, wasComment) = handleSlash()
        if (wasComment) {
          null.asInstanceOf[AstToken] // @nowarn — signal to continue loop
        } else {
          tok
        }
      case 61 => // =
        handleEqSign()
      case 63 => // ?
        if (!isOptionChainOp()) {
          // Fall through to operator/punc handling below
          dispatchDefault(ch, code)
        } else {
          next() // ?
          next() // .
          token(Token.Punc, "?.")
        }
      case 96 => // `
        readTemplateCharacters(begin = true)
      case 123 => // {
        braceCounter += 1
        token(Token.Punc, next())
      case 125 => // }
        braceCounter -= 1
        if (templateBraces.nonEmpty && templateBraces.last == braceCounter) {
          readTemplateCharacters(begin = false)
        } else {
          token(Token.Punc, next())
        }
      case _ =>
        dispatchDefault(ch, code)
    }
  }

  /** Handle the default dispatch cases: digits, punctuation, operators, identifiers, private names.
    */
  private def dispatchDefault(ch: String, code: Int): AstToken =
    if (isDigit(code)) {
      readNum()
    } else if (PUNC_CHARS.contains(ch.charAt(0))) {
      token(Token.Punc, next())
    } else if (OPERATOR_CHARS.contains(ch.charAt(0))) {
      readOperator()
    } else if (code == 92 || isIdentifierStart(ch)) { // 92 = backslash
      readWord()
    } else if (code == 35) { // #
      readPrivateWord()
    } else {
      parseError("Unexpected character '" + ch + "'")
    }

  // ---- Directive tracking (used by parser for "use strict" etc.) ----

  def addDirective(directive: String): Unit = {
    directiveStack.last += directive
    directives(directive) = directives.getOrElse(directive, 0) + 1
  }

  def pushDirectivesStack(): Unit =
    directiveStack += ArrayBuffer.empty

  def popDirectivesStack(): Unit = {
    val dirs = directiveStack.last
    for (d <- dirs)
      directives(d) = directives(d) - 1
    directiveStack.remove(directiveStack.length - 1)
  }

  def hasDirective(directive: String): Boolean =
    directives.getOrElse(directive, 0) > 0
}
