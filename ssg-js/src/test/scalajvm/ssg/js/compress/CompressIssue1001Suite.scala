/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for issue-1001 (parenthesized strings).
 * Ported from: terser/test/compress/issue-1001.js (1 test case)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }

final class CompressIssue1001Suite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // =========================================================================
  // parenthesis_strings_in_parenthesis
  // =========================================================================
  test("parenthesis_strings_in_parenthesis") {
    val input  = "var foo = ('(');\na(')');"
    val result = Terser.minifyToString(input, noOpt)
    assertEquals(result, "var foo=\"(\";a(\")\");")
  }
}
