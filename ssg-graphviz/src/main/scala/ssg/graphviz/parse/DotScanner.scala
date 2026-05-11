/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Lexer/scanner for the Graphviz DOT language.
 */
package ssg
package graphviz
package parse

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

enum TokenType {
  case Identifier, Number, QuotedString, HtmlString
  case Arrow, DashDash
  case LBrace, RBrace, LBracket, RBracket
  case Comma, Semicolon, Colon, Equals
  case Eof
}

final case class Token(tpe: TokenType, value: String, line: Int, col: Int)

class DotScanner(input: String) {

  private var pos:  Int = 0
  private var line: Int = 1
  private var col:  Int = 1

  def scan(): Array[Token] = {
    val tokens = ArrayBuffer.empty[Token]
    boundary {
      while (pos < input.length) {
        skipWhitespaceAndComments()
        if (pos >= input.length) {
          break(())
        }
        tokens += nextToken()
      }
    }
    tokens += Token(TokenType.Eof, "", line, col)
    tokens.toArray
  }

  private def peek(): Char = input.charAt(pos)

  private def advance(): Char = {
    val ch = input.charAt(pos)
    pos += 1
    if (ch == '\n') {
      line += 1
      col = 1
    } else {
      col += 1
    }
    ch
  }

  private def skipWhitespaceAndComments(): Unit = {
    boundary {
      while (pos < input.length) {
        val ch = peek()
        if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
          advance()
        } else if (ch == '/' && pos + 1 < input.length && input.charAt(pos + 1) == '/') {
          skipLineComment()
        } else if (ch == '/' && pos + 1 < input.length && input.charAt(pos + 1) == '*') {
          skipBlockComment()
        } else if (ch == '#') {
          skipLineComment()
        } else {
          break(())
        }
      }
    }
  }

  private def skipLineComment(): Unit = {
    boundary {
      while (pos < input.length) {
        if (advance() == '\n') {
          break(())
        }
      }
    }
  }

  private def skipBlockComment(): Unit = {
    val startLine = line
    val startCol = col
    advance() // /
    advance() // *
    boundary {
      while (pos < input.length) {
        if (peek() == '*' && pos + 1 < input.length && input.charAt(pos + 1) == '/') {
          advance() // *
          advance() // /
          break(())
        } else {
          advance()
        }
      }
    }
    if (pos >= input.length) {
      throw new IllegalArgumentException(
        s"Unterminated block comment starting at line $startLine, col $startCol"
      )
    }
  }

  private def nextToken(): Token = {
    val startLine = line
    val startCol = col
    val ch = peek()

    ch match {
      case '{' =>
        advance()
        Token(TokenType.LBrace, "{", startLine, startCol)
      case '}' =>
        advance()
        Token(TokenType.RBrace, "}", startLine, startCol)
      case '[' =>
        advance()
        Token(TokenType.LBracket, "[", startLine, startCol)
      case ']' =>
        advance()
        Token(TokenType.RBracket, "]", startLine, startCol)
      case ',' =>
        advance()
        Token(TokenType.Comma, ",", startLine, startCol)
      case ';' =>
        advance()
        Token(TokenType.Semicolon, ";", startLine, startCol)
      case ':' =>
        advance()
        Token(TokenType.Colon, ":", startLine, startCol)
      case '=' =>
        advance()
        Token(TokenType.Equals, "=", startLine, startCol)
      case '-' =>
        if (pos + 1 < input.length && input.charAt(pos + 1) == '>') {
          advance()
          advance()
          Token(TokenType.Arrow, "->", startLine, startCol)
        } else if (pos + 1 < input.length && input.charAt(pos + 1) == '-') {
          advance()
          advance()
          Token(TokenType.DashDash, "--", startLine, startCol)
        } else {
          readNumber(startLine, startCol)
        }
      case '"' =>
        readQuotedString(startLine, startCol)
      case '<' =>
        readHtmlString(startLine, startCol)
      case _ =>
        if (ch.isDigit || ch == '.') {
          readNumber(startLine, startCol)
        } else if (isIdStart(ch)) {
          readIdentifier(startLine, startCol)
        } else {
          throw new IllegalArgumentException(
            s"Unexpected character '${ch}' at line $startLine, col $startCol"
          )
        }
    }
  }

  private def isIdStart(ch: Char): Boolean =
    (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_' || (ch & 0xff) >= 0x80

  private def isIdPart(ch: Char): Boolean =
    isIdStart(ch) || (ch >= '0' && ch <= '9')

  private def readIdentifier(startLine: Int, startCol: Int): Token = {
    val sb = new StringBuilder
    boundary {
      while (pos < input.length) {
        val ch = peek()
        if (isIdPart(ch)) {
          sb.append(advance())
        } else {
          break(())
        }
      }
    }
    Token(TokenType.Identifier, sb.toString, startLine, startCol)
  }

  private def readNumber(startLine: Int, startCol: Int): Token = {
    val sb = new StringBuilder
    if (pos < input.length && peek() == '-') {
      sb.append(advance())
    }
    var hasDot = false
    boundary {
      while (pos < input.length) {
        val ch = peek()
        if (ch.isDigit) {
          sb.append(advance())
        } else if (ch == '.' && !hasDot) {
          hasDot = true
          sb.append(advance())
        } else {
          break(())
        }
      }
    }
    val value = sb.toString
    if (value == "-" || value == ".") {
      throw new IllegalArgumentException(
        s"Invalid number '$value' at line $startLine, col $startCol"
      )
    }
    Token(TokenType.Number, value, startLine, startCol)
  }

  private def readQuotedString(startLine: Int, startCol: Int): Token = {
    advance() // opening "
    val sb = new StringBuilder
    boundary {
      while (pos < input.length) {
        val ch = peek()
        if (ch == '\\' && pos + 1 < input.length) {
          advance() // backslash
          val escaped = advance()
          escaped match {
            case '"'  => sb.append('"')
            case '\\' => sb.append('\\')
            case 'n'  => sb.append('\n')
            case _    => sb.append('\\'); sb.append(escaped)
          }
        } else if (ch == '"') {
          advance() // closing "
          // DOT supports string concatenation: "abc" + "def"
          skipWhitespaceAndComments()
          if (pos < input.length && peek() == '+') {
            advance() // +
            skipWhitespaceAndComments()
            if (pos < input.length && peek() == '"') {
              val continuation = readQuotedString(line, col)
              sb.append(continuation.value)
            } else {
              throw new IllegalArgumentException(
                s"Expected quoted string after '+' at line $line, col $col"
              )
            }
          }
          break(())
        } else {
          sb.append(advance())
        }
      }
    }
    Token(TokenType.QuotedString, sb.toString, startLine, startCol)
  }

  private def readHtmlString(startLine: Int, startCol: Int): Token = {
    advance() // opening <
    val sb = new StringBuilder
    var depth = 1
    boundary {
      while (pos < input.length) {
        val ch = peek()
        if (ch == '<') {
          depth += 1
          sb.append(advance())
        } else if (ch == '>') {
          depth -= 1
          if (depth == 0) {
            advance() // closing >
            break(())
          } else {
            sb.append(advance())
          }
        } else {
          sb.append(advance())
        }
      }
    }
    if (depth != 0) {
      throw new IllegalArgumentException(
        s"Unterminated HTML string starting at line $startLine, col $startCol"
      )
    }
    Token(TokenType.HtmlString, sb.toString, startLine, startCol)
  }
}
