/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Hand-written lexer for the Liquid template language.
 * Replaces ANTLR-generated LiquidLexer.
 *
 * The lexer operates in 3 modes:
 *   DEFAULT: Scans for {{ and {% delimiters, collecting plain text
 *   IN_TAG: Inside {{ }} or {% %}, tokenizes operators/identifiers/literals
 *   IN_RAW: Inside {% raw %}...{% endraw %}, collects raw text
 */
package ssg
package liquid
package parser

import ssg.liquid.exceptions.LiquidException

import java.util.{ ArrayList, Set => JSet }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written lexer for Liquid templates. */
final class LiquidLexer(
  private val input:                 String,
  private val stripSpacesAroundTags: Boolean,
  private val stripSingleLine:       Boolean,
  private val blockNames:            JSet[String],
  private val tagNames:              JSet[String]
) {
  private var pos:    Int              = 0
  private var line:   Int              = 1
  private var col:    Int              = 0
  private val tokens: ArrayList[Token] = new ArrayList[Token]()

  /** Tokenizes the entire input and returns the token list. */
  def tokenize(): ArrayList[Token] = {
    while (pos < input.length())
      scanDefault()
    tokens.add(Token(TokenType.EOF, "", line, col))
    tokens
  }

  /** Scans in DEFAULT mode — looking for {{ and {% delimiters. */
  private def scanDefault(): Unit = boundary {
    val start     = pos
    val startLine = line
    val startCol  = col

    while (pos < input.length()) {
      if (pos + 1 < input.length()) {
        val c0 = input.charAt(pos)
        val c1 = input.charAt(pos + 1)

        if (c0 == '{' && c1 == '{') {
          // Emit accumulated text before the tag
          if (pos > start) {
            emitText(start, pos, startLine, startCol)
          }
          scanOutputTag()
          break(())
        } else if (c0 == '{' && c1 == '%') {
          // Emit accumulated text before the tag
          if (pos > start) {
            emitText(start, pos, startLine, startCol)
          }
          scanTagStart()
          break(())
        }
      }
      advanceChar()
    }

    // Remaining text
    if (pos > start) {
      emitText(start, pos, startLine, startCol)
    }
  }

  /** Emits a TEXT token, handling whitespace stripping if needed. */
  private def emitText(start: Int, end: Int, startLine: Int, startCol: Int): Unit = {
    val text = input.substring(start, end)
    if (text.nonEmpty) {
      tokens.add(Token(TokenType.TEXT, text, startLine, startCol))
    }
  }

  /** Scans an output tag: {{ ... }} */
  private def scanOutputTag(): Unit = {
    val startLine = line
    val startCol  = col

    // Consume {{ or {{-
    advance(2)
    val strip = pos < input.length() && input.charAt(pos) == '-'
    if (strip) {
      advance(1)
      stripTrailingWhitespaceFromLastText()
    }

    tokens.add(Token(TokenType.OUT_START, if (strip) "{{-" else "{{", startLine, startCol))

    // Scan tokens inside the tag
    scanInsideTag(isOutput = true)
  }

  /** Scans a tag: {% ... %} */
  private def scanTagStart(): Unit = {
    val startLine = line
    val startCol  = col

    // Consume {% or {%-
    advance(2)
    val strip = pos < input.length() && input.charAt(pos) == '-'
    if (strip) {
      advance(1)
      stripTrailingWhitespaceFromLastText()
    }

    tokens.add(Token(TokenType.TAG_START, if (strip) "{%-" else "{%", startLine, startCol))

    // Skip whitespace
    skipWhitespace()

    // Check for inline comment: {% # ... %}
    if (pos < input.length() && input.charAt(pos) == '#') {
      skipInlineComment()
      scanTagEnd()
    } else {
      // Read the tag identifier
      val tagId = scanTagIdentifier()

      if (tagId == "raw") {
        // Consume the tag end for raw
        skipWhitespace()
        scanTagEnd()
        // Now scan raw body
        scanRawBody()
      } else {
        // Continue scanning tag contents
        scanInsideTag(isOutput = false)
      }
    }
  }

  /** Scans the tag identifier after {% and emits the appropriate token. */
  private def scanTagIdentifier(): String = {
    val startLine = line
    val startCol  = col
    val idStart   = pos

    while (pos < input.length() && isIdContinue(input.charAt(pos)))
      advance(1)

    if (pos == idStart) {
      // Empty tag: {% %}
      ""
    } else {
      val id = input.substring(idStart, pos)

      val tokenType = id match {
        case "if"               => TokenType.IF
        case "elsif"            => TokenType.ELSIF
        case "endif"            => TokenType.ENDIF
        case "unless"           => TokenType.UNLESS
        case "endunless"        => TokenType.ENDUNLESS
        case "case"             => TokenType.CASE
        case "endcase"          => TokenType.ENDCASE
        case "when"             => TokenType.WHEN
        case "for"              => TokenType.FOR
        case "endfor"           => TokenType.ENDFOR
        case "tablerow"         => TokenType.TABLEROW
        case "endtablerow"      => TokenType.ENDTABLEROW
        case "capture"          => TokenType.CAPTURE
        case "endcapture"       => TokenType.ENDCAPTURE
        case "comment"          => TokenType.COMMENT
        case "endcomment"       => TokenType.ENDCOMMENT
        case "raw"              => TokenType.RAW
        case "assign"           => TokenType.ASSIGN
        case "include"          => TokenType.INCLUDE
        case "include_relative" => TokenType.INCLUDE_RELATIVE
        case "cycle"            => TokenType.CYCLE
        case "else"             => TokenType.ELSE
        case "break"            => TokenType.BREAK_TAG
        case "continue"         => TokenType.CONTINUE_TAG
        case "increment"        => TokenType.ID // treated as ID for increment/decrement tags
        case "decrement"        => TokenType.ID
        case other              =>
          if (blockNames.contains(other)) TokenType.BLOCK_ID
          else if (tagNames.contains(other)) TokenType.SIMPLE_TAG_ID
          else if (other.startsWith("end") && blockNames.contains(other.substring(3))) TokenType.END_BLOCK_ID
          else TokenType.ID
      }

      tokens.add(Token(tokenType, id, startLine, startCol))
      id
    }
  }

  /** Scans tokens inside a tag (both output {{ }} and tag {% %} bodies). */
  private def scanInsideTag(isOutput: Boolean): Unit = boundary {
    while (pos < input.length()) {
      skipWhitespace()

      if (pos >= input.length()) {
        throw new LiquidException("Unterminated tag", line, col)
      }

      val c = input.charAt(pos)

      // Check for tag end
      if (isOutput) {
        if (c == '-' && pos + 2 < input.length() && input.charAt(pos + 1) == '}' && input.charAt(pos + 2) == '}') {
          emitToken(TokenType.OUT_END, "-}}", 3)
          handlePostTagStripping()
          break(())
        }
        if (c == '}' && pos + 1 < input.length() && input.charAt(pos + 1) == '}') {
          emitToken(TokenType.OUT_END, "}}", 2)
          handlePostTagStripping()
          break(())
        }
      } else {
        if (c == '-' && pos + 2 < input.length() && input.charAt(pos + 1) == '%' && input.charAt(pos + 2) == '}') {
          emitToken(TokenType.TAG_END, "-%}", 3)
          handlePostTagStripping()
          break(())
        }
        if (c == '%' && pos + 1 < input.length() && input.charAt(pos + 1) == '}') {
          emitToken(TokenType.TAG_END, "%}", 2)
          handlePostTagStripping()
          break(())
        }
      }

      // Scan a token inside the tag
      scanInTagToken()
    }
  }

  /** Scans a single token inside a tag. */
  private def scanInTagToken(): Unit = {
    val c = input.charAt(pos)

    c match {
      case '\'' | '"' =>
        scanString(c)
      case '.' =>
        if (pos + 1 < input.length() && input.charAt(pos + 1) == '.') {
          emitToken(TokenType.DOTDOT, "..", 2)
        } else {
          emitToken(TokenType.DOT, ".", 1)
        }
      case '!' =>
        if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
          emitToken(TokenType.NEQ, "!=", 2)
        } else {
          advance(1) // skip unknown
        }
      case '<' =>
        if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
          emitToken(TokenType.LTEQ, "<=", 2)
        } else if (pos + 1 < input.length() && input.charAt(pos + 1) == '>') {
          emitToken(TokenType.NEQ, "<>", 2)
        } else {
          emitToken(TokenType.LT, "<", 1)
        }
      case '>' =>
        if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
          emitToken(TokenType.GTEQ, ">=", 2)
        } else {
          emitToken(TokenType.GT, ">", 1)
        }
      case '=' =>
        if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
          emitToken(TokenType.EQ, "==", 2)
        } else {
          emitToken(TokenType.EQ_SIGN, "=", 1)
        }
      case '|' => emitToken(TokenType.PIPE, "|", 1)
      case ':' => emitToken(TokenType.COLON, ":", 1)
      case ',' => emitToken(TokenType.COMMA, ",", 1)
      case '(' => emitToken(TokenType.OPAR, "(", 1)
      case ')' => emitToken(TokenType.CPAR, ")", 1)
      case '[' => emitToken(TokenType.OBR, "[", 1)
      case ']' => emitToken(TokenType.CBR, "]", 1)
      case '?' => emitToken(TokenType.QMARK, "?", 1)
      case '-' =>
        // Could be a negative number or minus operator
        if (pos + 1 < input.length() && isDigit(input.charAt(pos + 1))) {
          scanNumber()
        } else {
          emitToken(TokenType.MINUS, "-", 1)
        }
      case _ =>
        if (isDigit(c)) {
          scanNumber()
        } else if (isIdStart(c)) {
          scanIdentifierOrKeyword()
        } else {
          advance(1) // skip unknown char
        }
    }
  }

  /** Scans a string literal (single or double quoted). */
  private def scanString(quote: Char): Unit = {
    val startLine = line
    val startCol  = col
    advance(1) // skip opening quote
    val contentStart = pos

    while (pos < input.length() && input.charAt(pos) != quote)
      advanceChar()

    val content = input.substring(contentStart, pos)

    if (pos < input.length()) {
      advance(1) // skip closing quote
    }

    tokens.add(Token(TokenType.STR, content, startLine, startCol))
  }

  /** Scans a number (integer or double). */
  private def scanNumber(): Unit = {
    val startLine = line
    val startCol  = col
    val numStart  = pos

    if (pos < input.length() && input.charAt(pos) == '-') {
      advance(1)
    }

    while (pos < input.length() && isDigit(input.charAt(pos)))
      advance(1)

    // Check for decimal point (but not ..)
    if (
      pos < input.length() && input.charAt(pos) == '.' &&
      pos + 1 < input.length() && input.charAt(pos + 1) != '.'
    ) {
      if (pos + 1 < input.length() && isDigit(input.charAt(pos + 1))) {
        advance(1) // skip .
        while (pos < input.length() && isDigit(input.charAt(pos)))
          advance(1)
        tokens.add(Token(TokenType.DOUBLE_NUM, input.substring(numStart, pos), startLine, startCol))
      } else {
        // Just "123." — treat as double
        advance(1) // skip .
        tokens.add(Token(TokenType.DOUBLE_NUM, input.substring(numStart, pos), startLine, startCol))
      }
    } else {
      tokens.add(Token(TokenType.LONG_NUM, input.substring(numStart, pos), startLine, startCol))
    }
  }

  /** Scans an identifier or keyword. */
  private def scanIdentifierOrKeyword(): Unit = {
    val startLine = line
    val startCol  = col
    val idStart   = pos

    while (pos < input.length() && isIdContinue(input.charAt(pos)))
      advance(1)

    val id = input.substring(idStart, pos)

    val tokenType = id match {
      case "contains"     => TokenType.CONTAINS
      case "in"           => TokenType.IN
      case "and"          => TokenType.AND
      case "or"           => TokenType.OR
      case "true"         => TokenType.TRUE
      case "false"        => TokenType.FALSE
      case "nil" | "null" => TokenType.NIL
      case "with"         => TokenType.WITH
      case "offset"       => TokenType.OFFSET
      case "continue"     => TokenType.CONTINUE
      case "reversed"     => TokenType.REVERSED
      case "empty"        => TokenType.EMPTY
      case "blank"        => TokenType.BLANK
      case _              => TokenType.ID
    }

    tokens.add(Token(tokenType, id, startLine, startCol))
  }

  /** Scans raw block body until {% endraw %} is found. */
  private def scanRawBody(): Unit = boundary {
    val startLine = line
    val startCol  = col
    val bodyStart = pos

    while (pos < input.length())
      if (pos + 1 < input.length() && input.charAt(pos) == '{' && input.charAt(pos + 1) == '%') {
        val savedPos  = pos
        val savedLine = line
        val savedCol  = col
        advance(2) // skip {%
        skipWhitespace()

        val idStart = pos
        while (pos < input.length() && isIdContinue(input.charAt(pos)))
          advance(1)
        val id = input.substring(idStart, pos)

        if (id == "endraw") {
          // Emit the raw body text
          if (savedPos > bodyStart) {
            tokens.add(Token(TokenType.TEXT, input.substring(bodyStart, savedPos), startLine, startCol))
          }
          // Emit the endraw tag
          skipWhitespace()
          // Expect %}
          if (pos + 1 < input.length() && input.charAt(pos) == '%' && input.charAt(pos + 1) == '}') {
            tokens.add(Token(TokenType.TAG_START, "{%", savedLine, savedCol))
            tokens.add(Token(TokenType.RAW, "endraw", savedLine, savedCol))
            emitToken(TokenType.TAG_END, "%}", 2)
            handlePostTagStripping()
          }
          break(())
        } else {
          // Not endraw, continue scanning
          // Reset nothing - we already advanced, just keep going
        }
      } else {
        advanceChar()
      }

    // Unterminated raw block — emit everything as text
    if (pos > bodyStart) {
      tokens.add(Token(TokenType.TEXT, input.substring(bodyStart, pos), startLine, startCol))
    }
  }

  /** Scans a comment block body until {% endcomment %} */
  private def skipInlineComment(): Unit = boundary {
    // Skip everything after # until we hit %} or -%}
    while (pos < input.length()) {
      val c = input.charAt(pos)
      if (c == '%' && pos + 1 < input.length() && input.charAt(pos + 1) == '}') {
        break(()) // Don't consume %} — let the caller handle it
      }
      if (
        c == '-' && pos + 1 < input.length() && input.charAt(pos + 1) == '%' &&
        pos + 2 < input.length() && input.charAt(pos + 2) == '}'
      ) {
        break(()) // Don't consume -%}
      }
      advanceChar()
    }
  }

  /** After a closing tag with -, strip trailing whitespace from next text. */
  private def handlePostTagStripping(): Unit = {
    // The stripping of whitespace after -%} or -}} is handled here
    // by consuming whitespace characters after the tag end.
    val lastToken = tokens.get(tokens.size() - 1)
    if (lastToken.value.startsWith("-")) {
      while (pos < input.length() && isWhitespace(input.charAt(pos)))
        advanceChar()
    } else if (stripSpacesAroundTags) {
      if (stripSingleLine) {
        // Strip spaces/tabs and at most one linebreak
        while (pos < input.length() && (input.charAt(pos) == ' ' || input.charAt(pos) == '\t'))
          advance(1)
        if (pos < input.length() && (input.charAt(pos) == '\r' || input.charAt(pos) == '\n')) {
          if (input.charAt(pos) == '\r' && pos + 1 < input.length() && input.charAt(pos + 1) == '\n') {
            advance(2)
          } else {
            advance(1)
          }
        }
      } else {
        while (pos < input.length() && isWhitespace(input.charAt(pos)))
          advanceChar()
      }
    }
  }

  /** Scans the tag end (%} or -%}). */
  private def scanTagEnd(): Unit = {
    skipWhitespace()
    if (pos + 2 < input.length() && input.charAt(pos) == '-' && input.charAt(pos + 1) == '%' && input.charAt(pos + 2) == '}') {
      emitToken(TokenType.TAG_END, "-%}", 3)
      handlePostTagStripping()
    } else if (pos + 1 < input.length() && input.charAt(pos) == '%' && input.charAt(pos + 1) == '}') {
      emitToken(TokenType.TAG_END, "%}", 2)
      handlePostTagStripping()
    }
  }

  /** Strips trailing whitespace from the last TEXT token (for LHS stripping with {{- and {%-). */
  private def stripTrailingWhitespaceFromLastText(): Unit =
    if (!tokens.isEmpty) {
      val lastIdx = tokens.size() - 1
      val last    = tokens.get(lastIdx)
      if (last.tokenType == TokenType.TEXT) {
        var end = last.value.length()
        while (end > 0 && isWhitespace(last.value.charAt(end - 1)))
          end -= 1
        if (end == 0) {
          tokens.remove(lastIdx)
        } else if (end < last.value.length()) {
          tokens.set(lastIdx, Token(TokenType.TEXT, last.value.substring(0, end), last.line, last.col))
        }
      }
    }

  // --- Utility methods ---

  private def emitToken(tt: TokenType, value: String, len: Int): Unit = {
    tokens.add(Token(tt, value, line, col))
    advance(len)
  }

  private def advance(n: Int): Unit = {
    var i = 0
    while (i < n) {
      if (pos < input.length()) {
        if (input.charAt(pos) == '\n') {
          line += 1
          col = 0
        } else {
          col += 1
        }
        pos += 1
      }
      i += 1
    }
  }

  private def advanceChar(): Unit =
    if (pos < input.length()) {
      if (input.charAt(pos) == '\n') {
        line += 1
        col = 0
      } else {
        col += 1
      }
      pos += 1
    }

  private def skipWhitespace(): Unit =
    while (pos < input.length() && isWhitespace(input.charAt(pos)))
      advanceChar()

  private def isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\r' || c == '\n'
  private def isDigit(c:      Char): Boolean = c >= '0' && c <= '9'
  private def isLetter(c:     Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
  private def isIdStart(c:    Char): Boolean = isLetter(c) || c == '_'
  private def isIdContinue(c: Char): Boolean = isLetter(c) || isDigit(c) || c == '_' || c == '-'
}
