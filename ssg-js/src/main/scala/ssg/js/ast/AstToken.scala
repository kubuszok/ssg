/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * AST token representation for the JavaScript parser.
 *
 * Original source: terser lib/ast.js (AST_Token class)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_Token → AstToken
 *   Convention: Mutable flags via bitfield, same as Terser
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/ast.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 88493d7ca0d708389f5f78f541c4fb48e71d9fe2
 */
package ssg
package js
package ast

/** A token produced by the JavaScript tokenizer. */
final case class AstToken(
  tokenType:      String,
  value:          String,
  line:           Int,
  col:            Int,
  pos:            Int,
  var flags:      Int = 0,
  commentsBefore: List[AstToken] = Nil,
  commentsAfter:  List[AstToken] = Nil,
  file:           String = ""
) {
  def nlb:               Boolean = (flags & AstToken.FlagNlb) != 0
  def nlb_=(v: Boolean): Unit    =
    if (v) flags |= AstToken.FlagNlb else flags &= ~AstToken.FlagNlb

  def quote: String =
    if ((flags & AstToken.FlagQuoteExists) == 0) ""
    else if ((flags & AstToken.FlagQuoteSingle) != 0) "'"
    else "\""
  def quote_=(q: String): Unit = {
    if (q == "'") flags |= AstToken.FlagQuoteSingle else flags &= ~AstToken.FlagQuoteSingle
    if (q.nonEmpty) flags |= AstToken.FlagQuoteExists else flags &= ~AstToken.FlagQuoteExists
  }

  def templateEnd:               Boolean = (flags & AstToken.FlagTemplateEnd) != 0
  def templateEnd_=(v: Boolean): Unit    =
    if (v) flags |= AstToken.FlagTemplateEnd else flags &= ~AstToken.FlagTemplateEnd
}

object AstToken {
  val FlagNlb:         Int = 0x01
  val FlagQuoteSingle: Int = 0x02
  val FlagQuoteExists: Int = 0x04
  val FlagTemplateEnd: Int = 0x08

  /** Sentinel for missing tokens. */
  val Empty: AstToken = AstToken("", "", 0, 0, 0)
}
