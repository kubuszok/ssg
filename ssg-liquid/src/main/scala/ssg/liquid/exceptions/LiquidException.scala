/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/exceptions/LiquidException.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.exceptions → ssg.liquid.exceptions
 *   Convention: Removed ANTLR-specific constructors (RecognitionException, ParserRuleContext)
 *   Idiom: Scala 3 class, val parameters instead of public fields
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/exceptions/LiquidException.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package exceptions

/** Base exception for Liquid template parsing and rendering errors.
  *
  * Includes line and column position information for error reporting.
  */
class LiquidException(
  message:                String,
  val line:               Int,
  val charPositionInLine: Int,
  cause:                  Throwable
) extends RuntimeException(message, cause) {

  def this(message: String, line: Int, charPositionInLine: Int) =
    this(message, line, charPositionInLine, null)

  def this(message: String) =
    this(message, -1, -1, null)

  def this(message: String, cause: Throwable) =
    this(message, -1, -1, cause)
}

object LiquidException {

  /** Creates a LiquidException with a formatted error message showing the offending line and position. */
  def withContext(message: String, input: String, line: Int, charPositionInLine: Int): LiquidException = {
    val inputLines = input.split("\r?\n|\r")
    val errorLine  = if (line >= 1 && line <= inputLines.length) inputLines(line - 1) else ""

    val sb = new StringBuilder()
    sb.append(s"\nError on line $line, column $charPositionInLine:\n")
    sb.append(errorLine).append("\n")
    var i = 0
    while (i < charPositionInLine) {
      sb.append(" ")
      i += 1
    }
    sb.append("^\n")
    sb.append(message)

    new LiquidException(sb.toString(), line, charPositionInLine)
  }
}
