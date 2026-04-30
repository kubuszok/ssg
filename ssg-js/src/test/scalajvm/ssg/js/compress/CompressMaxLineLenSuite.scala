/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Output tests for max_line_len option.
 * Ported from: terser/test/compress/max_line_len.js (2 test cases)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support beautify format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.output.OutputOptions

final class CompressMaxLineLenSuite extends munit.FunSuite {

  private val input = "function f(a) {\n    return { c: 42, d: a(), e: \"foo\"};\n}"

  // =========================================================================
  // too_short
  // =========================================================================
  test("too_short") {
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(maxLineLen = 10))
    )
    val expected = "function f(a){\nreturn{\nc:42,\nd:a(),\ne:\"foo\"}}"
    assertEquals(result, expected)
  }

  // =========================================================================
  // just_enough
  // =========================================================================
  test("just_enough") {
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(maxLineLen = 14))
    )
    val expected = "function f(a){\nreturn{c:42,\nd:a(),e:\"foo\"}\n}"
    assertEquals(result, expected)
  }
}
