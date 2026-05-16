/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The resulting token returned from `lex`.
 *
 * Original source: katex src/Token.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex

import scala.language.implicitConversions

import lowlevel.Nullable

/** The resulting token returned from `lex`.
  *
  * It consists of the token text plus some position information. The position information is essentially a range in an input string, but instead of referencing the bare input string, we refer to the
  * lexer. That way it is possible to attach extra metadata to the input string, like for example a file name or similar.
  *
  * The position information is optional, so it is OK to construct synthetic tokens if appropriate. Not providing available position information may lead to degraded error reporting, though.
  */
final class Token(
  var text:         String, // the text of this token
  var loc:          Nullable[SourceLocation] = Nullable.Null, // source location
  var noexpand:     Nullable[Boolean] = Nullable.Null, // don't expand the token
  var treatAsRelax: Nullable[Boolean] = Nullable.Null // used in \noexpand
) extends SourceLocation.HasLoc {

  /** Given a pair of tokens (this and endToken), compute a `Token` encompassing the whole input range enclosed by these two.
    */
  def range(
    endToken: Token, // last token of the range, inclusive
    text:     String // the text of the newly constructed token
  ): Token =
    Token(text, SourceLocation.range(this, endToken))
}
