/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for issue-1787 (unary prefix after inlining).
 * Ported from: terser/test/compress/issue-1787.js (1 test case)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressIssue1787Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // =========================================================================
  // unary_prefix — inlining preserves unary prefix -(2/3)
  // =========================================================================
  // Known gap: compressor does not inline IIFE with reduce_vars+inline yet
  test("unary_prefix".fail) {
    assertCompresses(
      input = "console.log(function() {\n    var x = -(2 / 3);\n    return x;\n}());",
      expected = "console.log(-2/3);",
      options = AllOff.copy(
        evaluate = true,
        inline = InlineLevel.InlineFull,
        reduceFuncs = true,
        reduceVars = true,
        unused = true
      )
    )
  }
}
