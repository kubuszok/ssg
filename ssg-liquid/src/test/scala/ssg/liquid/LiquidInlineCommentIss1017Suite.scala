/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.exceptions.LiquidException

/** Differential tests for ISS-1017: inline-comment (`{% # ... %}`) line-prefix strictness.
  *
  * liqp/Liquid oracle (original-src/liqp/src/main/antlr4/liquid/parser/v4/LiquidLexer.g4):
  *
  * CommentInTag : ( '#' ( {!linebreakOrTagEndAhead()}? . )* [ \t\r\n]* )+ -> channel(HIDDEN); (g4:119) CommentInTagId : ( '#' ( {!linebreakOrTagEndAhead()}? . )* [ \t\r\n]* )+ -> channel(HIDDEN),
  * popMode; (g4:194)
  *
  * linebreakOrTagEndAhead() (g4:69-73) returns true at a line break or just before `%}`/`-%}`.
  *
  * Each repeated group is `#` + rest-of-line (the inner `.` repeat stops at a line break or at the tag end via `linebreakOrTagEndAhead()`) + trailing whitespace including newlines. The outer `+`
  * REQUIRES every continuation line, after inter-line whitespace, to begin with `#`.
  *
  * A continuation line whose first non-whitespace char is NOT `#` (and is not the tag end) ends the comment token mid-tag; the leftover characters become visible tokens that break
  * `empty_tag : TagStart TagEnd` (LiquidParser.g4:116-119), so the parser raises a syntax error.
  *
  * Therefore: a malformed multi-line inline comment (a continuation line not starting with `#`) must surface a LiquidException at parse time. Asserting at the Template.parse(...) level keeps this
  * faithful whether the port reports the failure during lexing or during parsing.
  */
final class LiquidInlineCommentIss1017Suite extends munit.FunSuite {

  // THE RED ASSERTION: continuation line `  line two` has no leading `#`, so the comment token
  // ends mid-tag and the leftover content fails empty_tag — upstream raises a syntax error.
  test("ISS-1017 malformed multiline inline comment (2nd line lacks #) raises LiquidException") {
    intercept[LiquidException] {
      Template.parse("{% # line one\n  line two %}")
    }
  }

  // GUARD: a single-line inline comment is well-formed and renders to nothing.
  test("ISS-1017 single-line inline comment is accepted and renders empty") {
    assertEquals(Template.parse("{% # just a comment %}").render(), "")
  }

  // GUARD: a multi-line inline comment where every line begins with `#` is well-formed and
  // renders to nothing (the indented `#` on the second line satisfies the outer `+`).
  test("ISS-1017 well-formed multiline inline comment (every line starts with #) renders empty") {
    assertEquals(Template.parse("{% # line one\n   # line two %}").render(), "")
  }
}
