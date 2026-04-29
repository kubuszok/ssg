/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse error tests.
 * Ported from: terser/test/compress/parse_errors.js (2 test cases)
 *
 * These tests verify that the parser produces the correct error messages
 * for invalid JavaScript input. Uses bad_input/expect_error format.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support bad_input format. */
package ssg
package js
package compress

import ssg.js.parse.{ JsParseError, Parser }

final class CompressParseErrorsSuite extends munit.FunSuite {

  // =========================================================================
  // basic_syntax_error
  // =========================================================================
  test("basic_syntax_error") {
    val ex = intercept[JsParseError] {
      new Parser().parse("var x = 5--;")
    }
    assert(
      ex.message.contains("Invalid use of -- operator"),
      s"Expected 'Invalid use of -- operator', got: ${ex.getMessage}"
    )
  }

  // =========================================================================
  // invalid_template_string_example
  // =========================================================================
  test("invalid_template_string_example") {
    val ex = intercept[JsParseError] {
      new Parser().parse("console.log(`foo ${100 + 23}\n")
    }
    assert(
      ex.message.contains("Unterminated template"),
      s"Expected 'Unterminated template', got: ${ex.getMessage}"
    )
  }
}
