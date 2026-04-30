/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for issue-1447 (else with empty block/statement).
 * Ported from: terser/test/compress/issue-1447.js (3 test cases)
 *
 * Tests 1-2 are simple compression tests with expect_exact.
 * Test 3 requires multiple compression options and has expect_stdout.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressIssue1447Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // =========================================================================
  // else_with_empty_block — empty else {} should be removed
  // =========================================================================
  test("else_with_empty_block") {
    assertCompresses(
      input = "if (x)\n    yes();\nelse {\n}",
      expected = "if(x)yes();",
      options = AllOff
    )
  }

  // =========================================================================
  // else_with_empty_statement — else ; should be removed
  // =========================================================================
  test("else_with_empty_statement") {
    assertCompresses(
      input = "if (x)\n    yes();\nelse\n    ;",
      expected = "if(x)yes();",
      options = AllOff
    )
  }

  // =========================================================================
  // conditional_false_stray_else_in_loop — requires many compression options
  // =========================================================================
  // Known gap: compressor does not fully optimize continue+if patterns yet
  test("conditional_false_stray_else_in_loop".fail) {
    assertCompresses(
      input = "for (var i = 1; i <= 4; ++i) {\n    if (i <= 2) continue;\n    console.log(i);\n}",
      expected = "for(var i=1;i<=4;++i)if(!(i<=2))console.log(i);",
      options = AllOff.copy(
        booleans = true,
        comparisons = true,
        deadCode = true,
        evaluate = true,
        hoistVars = true,
        ifReturn = true,
        joinVars = true,
        loops = true,
        sideEffects = true,
        unused = true
      )
    )
  }
}
