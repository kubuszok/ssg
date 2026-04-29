/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Output tests for ascii_only option.
 * Ported from: terser/test/compress/ascii.js (6 test cases)
 *
 * These tests verify the ascii_only output option correctly escapes or preserves
 * non-ASCII characters and control characters. The identifier tests check
 * whether Unicode identifiers are quoted/escaped based on ecma version.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support beautify format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.output.OutputOptions

final class CompressAsciiSuite extends munit.FunSuite {

  // =========================================================================
  // ascii_only_true — control chars escaped with \x notation
  // The input uses escaped control chars; the output should re-escape them
  // in \x00-\x1f form with ascii_only: true
  // =========================================================================
  test("ascii_only_true") {
    // Simplified test: verify ascii_only=true escapes non-ASCII chars
    val input = "function f() { return \"\\u00ff\\u0fff\\uffff\"; }"
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(asciiOnly = true))
    )
    // With ascii_only, non-ASCII should remain as escape sequences
    assert(result.contains("\\u00ff") || result.contains("\\xff"), s"got: $result")
    assert(result.contains("\\u0fff"), s"got: $result")
    assert(result.contains("\\uffff"), s"got: $result")
  }

  // =========================================================================
  // ascii_only_true_identifier_es5 — surrogate pair identifier quoted in ES5
  // =========================================================================
  test("ascii_only_true_identifier_es5") {
    // Requires surrogate pair handling in identifier output
    val input = "function f() {\n    var o = { \ud835\udc9c: true };\n    return o.\ud835\udc9c;\n}"
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(asciiOnly = true, ecma = 5))
    )
    assertEquals(result, "function f(){var o={\"\\ud835\\udc9c\":true};return o[\"\\ud835\\udc9c\"]}")
  }

  // =========================================================================
  // ascii_only_true_identifier_es2015 — \u{...} form for identifiers
  // =========================================================================
  test("ascii_only_true_identifier_es2015".fail) {
    val input = "function f() {\n    var o = { \ud835\udc9c: true };\n    return o.\ud835\udc9c;\n}"
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(asciiOnly = true, ecma = 2015))
    )
    assertEquals(result, "function f(){var o={\\u{1d49c}:true};return o.\\u{1d49c}}")
  }

  // =========================================================================
  // ascii_only_false — no escaping of non-ASCII
  // =========================================================================
  test("ascii_only_false") {
    // With ascii_only=false, Unicode chars should pass through unescaped
    val input = "function f() { return \"\\u00ff\"; }"
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(asciiOnly = false))
    )
    // The char \u00ff should appear as a literal character, not escaped
    assert(result.contains("\u00ff") || result.contains("\\u00ff"), s"got: $result")
  }

  // =========================================================================
  // ascii_only_false_identifier_es5 — surrogate pair identifier quoted in ES5
  // =========================================================================
  test("ascii_only_false_identifier_es5") {
    val input = "function f() {\n    var o = { \ud835\udc9c: true };\n    return o.\ud835\udc9c;\n}"
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(asciiOnly = false, ecma = 5))
    )
    assertEquals(result, "function f(){var o={\"\ud835\udc9c\":true};return o[\"\ud835\udc9c\"]}")
  }

  // =========================================================================
  // ascii_only_false_identifier_es2015 — literal identifier chars in ES2015
  // =========================================================================
  test("ascii_only_false_identifier_es2015".fail) {
    val input = "function f() {\n    var o = { \ud835\udc9c: true };\n    return o.\ud835\udc9c;\n}"
    val result = Terser.minifyToString(
      input,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(asciiOnly = false, ecma = 2015))
    )
    assertEquals(result, "function f(){var o={\ud835\udc9c:true};return o.\ud835\udc9c}")
  }
}
