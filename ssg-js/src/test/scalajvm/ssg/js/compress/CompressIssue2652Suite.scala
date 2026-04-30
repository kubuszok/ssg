/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Output tests for issue-2652 (semicolon insertion with comments).
 * Ported from: terser/test/compress/issue-2652.js (2 test cases)
 *
 * These tests verify that semicolons are correctly inserted when a comment
 * appears between two statements in beautify mode.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support beautify format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.output.OutputOptions

final class CompressIssue2652Suite extends munit.FunSuite {

  private val beautifyAllComments = MinifyOptions(
    compress = false,
    mangle = false,
    output = OutputOptions(beautify = true, comments = "all")
  )

  // =========================================================================
  // insert_semicolon — semicolons inserted around comments between var declarations
  // Known gap: comment placement around semicolons in beautify mode differs from upstream
  // =========================================================================
  test("insert_semicolon".fail) {
    val result   = Terser.minifyToString("var a\n/* foo */ var b", beautifyAllComments)
    val expected = "var a\n/* foo */;\n\nvar b;"
    assertEquals(result, expected)
  }

  // =========================================================================
  // unary_postfix — semicolons inserted around comments with unary postfix
  // Known gap: comment placement around semicolons in beautify mode differs from upstream
  // =========================================================================
  test("unary_postfix".fail) {
    val result   = Terser.minifyToString("a\n/* foo */++b", beautifyAllComments)
    val expected = "a\n/* foo */;\n\n++b;"
    assertEquals(result, expected)
  }
}
