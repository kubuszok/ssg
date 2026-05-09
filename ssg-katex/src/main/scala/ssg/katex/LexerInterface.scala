/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Interface required to break circular dependency between Token, Lexer, and
 * ParseError.
 *
 * Original source: katex src/Token.ts (LexerInterface)
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex

import scala.util.matching.Regex

/**
 * Interface required to break circular dependency between Token, Lexer, and
 * ParseError.
 */
trait LexerInterface {
  def input: String
  def tokenRegex: Regex
}
