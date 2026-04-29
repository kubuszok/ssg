/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for issue-1943 (dot-access with line comment before property).
 * Ported from: terser/test/compress/issue-1943.js (4 test cases)
 *
 * These are expect_exact tests — parse input and verify exact output.
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }

final class CompressIssue1943Suite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // =========================================================================
  // operator — line comment before typeof after dot
  // =========================================================================
  test("operator") {
    val result = Terser.minifyToString("a. //comment\ntypeof", noOpt)
    assertEquals(result, "a.typeof;")
  }

  // =========================================================================
  // name — line comment before name after dot
  // =========================================================================
  test("name") {
    val result = Terser.minifyToString("a. //comment\nb", noOpt)
    assertEquals(result, "a.b;")
  }

  // =========================================================================
  // keyword — line comment before keyword after dot
  // =========================================================================
  test("keyword") {
    val result = Terser.minifyToString("a. //comment\ndefault", noOpt)
    assertEquals(result, "a.default;")
  }

  // =========================================================================
  // atom — line comment before atom after dot
  // =========================================================================
  test("atom") {
    val result = Terser.minifyToString("a. //comment\ntrue", noOpt)
    assertEquals(result, "a.true;")
  }
}
