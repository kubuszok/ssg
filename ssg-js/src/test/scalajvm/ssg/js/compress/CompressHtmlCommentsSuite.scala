/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for HTML comment handling.
 * Ported from: terser/test/compress/html_comments.js (12 test cases)
 *
 * These tests verify that HTML comments (<!-- and -->) are handled correctly
 * in the parser/output pipeline. The ssg-js parser supports HTML comments
 * as line comments. Tests are expect_exact format.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }

final class CompressHtmlCommentsSuite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // =========================================================================
  // html_comment_in_expression
  // =========================================================================
  test("html_comment_in_expression") {
    val result = Terser.minifyToString("function f(a, b, x, y) { return a < !--b && x-- > y; }", noOpt)
    assertEquals(result, "function f(a,b,x,y){return a<! --b&&x-- >y}")
  }

  // =========================================================================
  // html_comment_in_less_than
  // =========================================================================
  test("html_comment_in_less_than") {
    val result = Terser.minifyToString("function f(a, b) { return a < !--b; }", noOpt)
    assertEquals(result, "function f(a,b){return a<! --b}")
  }

  // =========================================================================
  // html_comment_in_left_shift
  // =========================================================================
  test("html_comment_in_left_shift") {
    val result = Terser.minifyToString("function f(a, b) { return a << !--b; }", noOpt)
    assertEquals(result, "function f(a,b){return a<<! --b}")
  }

  // =========================================================================
  // html_comment_in_right_shift
  // =========================================================================
  test("html_comment_in_right_shift") {
    val result = Terser.minifyToString("function f(a, b) { return a-- >> b; }", noOpt)
    assertEquals(result, "function f(a,b){return a-- >>b}")
  }

  // =========================================================================
  // html_comment_in_zero_fill_right_shift
  // =========================================================================
  test("html_comment_in_zero_fill_right_shift") {
    val result = Terser.minifyToString("function f(a, b) { return a-- >>> b; }", noOpt)
    assertEquals(result, "function f(a,b){return a-- >>>b}")
  }

  // =========================================================================
  // html_comment_in_greater_than
  // =========================================================================
  test("html_comment_in_greater_than") {
    val result = Terser.minifyToString("function f(a, b) { return a-- > b; }", noOpt)
    assertEquals(result, "function f(a,b){return a-- >b}")
  }

  // =========================================================================
  // html_comment_in_greater_than_or_equal
  // =========================================================================
  test("html_comment_in_greater_than_or_equal") {
    val result = Terser.minifyToString("function f(a, b) { return a-- >= b; }", noOpt)
    assertEquals(result, "function f(a,b){return a-- >=b}")
  }

  // =========================================================================
  // html_comment_in_string_literal
  // =========================================================================
  test("html_comment_in_string_literal") {
    val result = Terser.minifyToString(
      "function f() { return \"<!--HTML-->comment in<!--string literal-->\"; }",
      noOpt
    )
    assertEquals(result, "function f(){return\"\\x3c!--HTML--\\x3ecomment in\\x3c!--string literal--\\x3e\"}")
  }

  // =========================================================================
  // script_tag_in_comparison
  // =========================================================================
  test("script_tag_in_comparison") {
    val result = Terser.minifyToString("function f() { return 0 < (/script>/).test(); }", noOpt)
    assertEquals(result, "function f(){return 0< /script>/.test()}")
  }

  // =========================================================================
  // html_comment_in_negated_comparison
  // =========================================================================
  test("html_comment_in_negated_comparison") {
    val result = Terser.minifyToString("function f() { return 1 < (!--x + 1); }", noOpt)
    assertEquals(result, "function f(){return 1<! --x+1}")
  }

  // =========================================================================
  // html_comment_in_negated_comparison_2
  // =========================================================================
  test("html_comment_in_negated_comparison_2") {
    val result = Terser.minifyToString("function f() { return !x-- > 1; }", noOpt)
    assertEquals(result, "function f(){return!x-- >1}")
  }

  // =========================================================================
  // html_comment_after_multiline_comment — comment line after */ is treated
  // as a comment and stripped
  // =========================================================================
  test("html_comment_after_multiline_comment") {
    val input  = "var foo; /*\n*/-->   var bar;\n        var foobar;"
    val result = Terser.minifyToString(input, noOpt)
    assertEquals(result, "var foo;var foobar;")
  }
}
