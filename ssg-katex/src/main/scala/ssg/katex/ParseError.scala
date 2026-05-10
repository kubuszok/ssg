/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This is the ParseError class, which is the main error thrown by KaTeX
 * functions when something has gone wrong. This is used to distinguish internal
 * errors from errors in the expression that the user provided.
 *
 * Original source: katex src/ParseError.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex

import ssg.commons.Nullable

/** This is the ParseError class, which is the main error thrown by KaTeX functions when something has gone wrong. This is used to distinguish internal errors from errors in the expression that the
  * user provided.
  *
  * If possible, a caller should provide a Token or ParseNode with information about where in the source string the problem occurred.
  */
class ParseError(
  /** The underlying error message without any context added. */
  val rawMessage: String,
  token:          Nullable[SourceLocation.HasLoc] = Nullable.Null
) extends Exception(ParseError.buildMessage(rawMessage, token)) {

  /** Error start position based on passed-in Token or ParseNode. */
  val position: Nullable[Int] = ParseError.computePosition(token)

  /** Length of affected text based on passed-in Token or ParseNode. */
  val length: Nullable[Int] = ParseError.computeLength(token)
}

object ParseError {

  private def buildMessage(
    message: String,
    token:   Nullable[SourceLocation.HasLoc]
  ): String = {
    var error  = "KaTeX parse error: " + message
    val locOpt = token.flatMap(_.loc)
    locOpt.foreach { loc =>
      if (loc.start <= loc.end) {
        // If we have the input and a position, make the error a bit fancier

        // Get the input
        val input = loc.lexer.input

        // Prepend some information
        val start = loc.start
        val end   = loc.end
        if (start == input.length) {
          error += " at end of input: "
        } else {
          error += " at position " + (start + 1) + ": "
        }

        // Underline token in question using combining underscores
        // U+0332 COMBINING LOW LINE
        val combiningUnderscore = "̲"
        val underlined          = input.slice(start, end).map(ch => ch.toString + combiningUnderscore).mkString

        // Extract some context from the input and add it to the error
        val left =
          if (start > 15) {
            "…" + input.slice(start - 15, start)
          } else {
            input.slice(0, start)
          }
        val right =
          if (end + 15 < input.length) {
            input.slice(end, end + 15) + "…"
          } else {
            input.slice(end, input.length)
          }
        error += left + underlined + right
      }
    }
    error
  }

  private def computePosition(
    token: Nullable[SourceLocation.HasLoc]
  ): Nullable[Int] =
    token.flatMap(_.loc).flatMap { loc =>
      if (loc.start <= loc.end) Nullable(loc.start)
      else Nullable.Null
    }

  private def computeLength(
    token: Nullable[SourceLocation.HasLoc]
  ): Nullable[Int] =
    token.flatMap(_.loc).flatMap { loc =>
      if (loc.start <= loc.end) Nullable(loc.end - loc.start)
      else Nullable.Null
    }
}
