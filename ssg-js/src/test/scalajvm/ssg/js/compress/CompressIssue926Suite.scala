/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for issue-926 (template strings).
 * Ported from: terser/test/compress/issue-926.js (1 test case)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }

final class CompressIssue926Suite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // =========================================================================
  // template_strings — template literal in function call
  // =========================================================================
  test("template_strings") {
    val input  = "foo(\n    `<span>${contents}</span>`,\n    `<a href=\"${url}\">${text}</a>`\n);"
    val result = Terser.minifyToString(input, noOpt)
    assertEquals(result, "foo(`<span>${contents}</span>`,`<a href=\"${url}\">${text}</a>`);")
  }
}
