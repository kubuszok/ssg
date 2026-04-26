/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Hand-written token types for the Liquid template language.
 * Replaces ANTLR-generated token vocabulary.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package parser

/** Token types for the Liquid template language lexer. */
enum TokenType {
  // Structural
  case TEXT // Plain text outside tags
  case OUT_START // {{ (output start)
  case OUT_END // }} (output end)
  case TAG_START // {% (tag start)
  case TAG_END // %} (tag end)
  case EOF // End of input

  // Literals
  case STR // 'string' or "string"
  case LONG_NUM // 123, -45
  case DOUBLE_NUM // 1.5, -3.14

  // Identifiers and keywords
  case ID // identifier
  case DOT // .
  case DOTDOT // ..

  // Operators
  case EQ // ==
  case NEQ // != or <>
  case LT // <
  case GT // >
  case LTEQ // <=
  case GTEQ // >=
  case EQ_SIGN // = (assignment)
  case PIPE // |
  case COLON // :
  case COMMA // ,
  case MINUS // -
  case OPAR // (
  case CPAR // )
  case OBR // [
  case CBR // ]
  case QMARK // ?

  // Keywords (inside tags)
  case CONTAINS
  case IN
  case AND
  case OR
  case TRUE
  case FALSE
  case NIL
  case WITH
  case OFFSET
  case CONTINUE
  case REVERSED
  case EMPTY
  case BLANK

  // Tag identifiers (from IN_TAG_ID mode)
  case IF, ELSIF, ENDIF
  case UNLESS, ENDUNLESS
  case CASE, ENDCASE, WHEN
  case FOR, ENDFOR
  case TABLEROW, ENDTABLEROW
  case CAPTURE, ENDCAPTURE
  case COMMENT, ENDCOMMENT
  case RAW // raw tag (body is collected as TEXT)
  case ASSIGN
  case INCLUDE
  case INCLUDE_RELATIVE
  case CYCLE
  case ELSE
  case BREAK_TAG // break (renamed to avoid Scala keyword conflict)
  case CONTINUE_TAG // continue as a tag name

  // Custom tags/blocks
  case BLOCK_ID // custom block tag name
  case END_BLOCK_ID // end of custom block tag
  case SIMPLE_TAG_ID // custom simple tag name
}

/** A token produced by the lexer. */
final case class Token(
  tokenType: TokenType,
  value:     String,
  line:      Int,
  col:       Int
) {

  override def toString: String = s"Token($tokenType, ${if (value.length > 20) value.substring(0, 20) + "..." else value}, $line:$col)"
}
