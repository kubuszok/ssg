/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Lexing or parsing positional information for error reporting.
 *
 * Original source: katex src/SourceLocation.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex

import scala.language.implicitConversions

import ssg.commons.Nullable

/**
 * Lexing or parsing positional information for error reporting.
 * This object is immutable.
 */
final case class SourceLocation(
    lexer: LexerInterface, // Lexer holding the input string.
    start: Int,            // Start offset, zero-based inclusive.
    end: Int               // End offset, zero-based exclusive.
)

object SourceLocation {

  /** Trait for objects that carry an optional source location. */
  trait HasLoc {
    def loc: Nullable[SourceLocation]
  }

  /**
   * Merges two `SourceLocation`s from location providers, given they are
   * provided in order of appearance.
   * - Returns the first one's location if only the second is not provided.
   * - Returns a merged range of the first and the last if both are provided
   *   and their lexers match.
   * - Otherwise, returns null.
   */
  def range(
      first: Nullable[HasLoc],
      second: Nullable[HasLoc]
  ): Nullable[SourceLocation] = {
    if (second.isEmpty) {
      first.flatMap(_.loc)
    } else if (first.isEmpty || first.flatMap(_.loc).isEmpty || second.flatMap(_.loc).isEmpty ||
        (first.get.loc.get.lexer ne second.get.loc.get.lexer)) {
      Nullable.Null
    } else {
      SourceLocation(
        first.get.loc.get.lexer,
        first.get.loc.get.start,
        second.get.loc.get.end
      )
    }
  }
}
