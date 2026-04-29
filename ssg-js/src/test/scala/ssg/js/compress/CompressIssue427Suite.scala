/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for issue-427 (wrap_func_args output option).
 * Ported from: terser/test/compress/issue-427.js (2 test cases)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support beautify format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.output.OutputOptions

final class CompressIssue427Suite extends munit.FunSuite {

  private val input = "console.log(function() {\n    return \"test\";\n}, () => null);"

  // =========================================================================
  // wrap_func_args
  // =========================================================================
  test("wrap_func_args") {
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(wrapFuncArgs = true))
    )
    assertEquals(result, "console.log((function(){return\"test\"}),(()=>null));")
  }

  // =========================================================================
  // no_wrap_func_args
  // =========================================================================
  test("no_wrap_func_args") {
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(wrapFuncArgs = false))
    )
    assertEquals(result, "console.log(function(){return\"test\"},()=>null);")
  }
}
