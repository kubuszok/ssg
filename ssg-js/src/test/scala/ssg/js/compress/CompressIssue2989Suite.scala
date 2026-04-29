/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Output tests for issue-2989 (inline_script option).
 * Ported from: terser/test/compress/issue-2989.js (2 test cases)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support beautify format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.output.OutputOptions

final class CompressIssue2989Suite extends munit.FunSuite {

  // =========================================================================
  // inline_script_off — </script> is NOT escaped
  // =========================================================================
  test("inline_script_off") {
    val result = Terser.minifyToString(
      "console.log(\"</sCrIpT>\");",
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(inlineScript = false))
    )
    assertEquals(result, "console.log(\"</sCrIpT>\");")
  }

  // =========================================================================
  // inline_script_on — </script> IS escaped
  // =========================================================================
  test("inline_script_on") {
    val result = Terser.minifyToString(
      "console.log(\"</sCrIpT>\");",
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(inlineScript = true))
    )
    assertEquals(result, "console.log(\"<\\/sCrIpT>\");")
  }
}
