/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for issue-229 (spread in object literal).
 * Ported from: terser/test/compress/issue-229.js (1 test case)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }

final class CompressIssue229Suite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // =========================================================================
  // template_strings — spread in object literal
  // =========================================================================
  test("template_strings") {
    val input = "var x = {};\nvar y = {...x};\ny.hello = 'world';"
    val result = Terser.minifyToString(input, noOpt)
    assertEquals(result, "var x={};var y={...x};y.hello=\"world\";")
  }
}
