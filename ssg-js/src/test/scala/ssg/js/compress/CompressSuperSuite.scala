/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for super expressions.
 * Ported from: terser/test/compress/super.js (1 test case)
 *
 * These are expect_exact tests — parse the input and verify exact output string.
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }

final class CompressSuperSuite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // =========================================================================
  // super_can_be_parsed
  // =========================================================================
  test("super_can_be_parsed") {
    val result = Terser.minifyToString("super(1,2);\nsuper.meth();", noOpt)
    assertEquals(result, "super(1,2);super.meth();")
  }
}
