/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Mangle tests for issue-892 (don't mangle arguments).
 * Ported from: terser/test/compress/issue-892.js (1 test case)
 *
 * This test uses expect_exact + expect_stdout with mangle options.
 * The mangle integration via Terser.minify() is tested.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support mangle format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.scope.ManglerOptions

final class CompressIssue892Suite extends munit.FunSuite {

  // =========================================================================
  // dont_mangle_arguments — arguments should not be renamed during mangling
  // =========================================================================
  test("dont_mangle_arguments") {
    val input  = "function f() { return arguments; }"
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = ManglerOptions())
    )
    assertEquals(result, "function f(){return arguments}")
  }
}
