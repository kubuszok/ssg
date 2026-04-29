/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for BigInt literals.
 * Ported from: terser/test/compress/big_int.js (10 test cases)
 *
 * Tests 1-6: expect_exact parse+output tests
 * Tests 7-8: bad_input parse error tests
 * Tests 9-10: compression tests with expect_exact (require compressor)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.parse.{ JsParseError, Parser }

final class CompressBigIntSuite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // =========================================================================
  // big_int_positive
  // =========================================================================
  test("big_int_positive") {
    assertEquals(Terser.minifyToString("1000n", noOpt), "1000n;")
  }

  // =========================================================================
  // big_int_negative
  // =========================================================================
  test("big_int_negative") {
    assertEquals(Terser.minifyToString("-15n", noOpt), "-15n;")
  }

  // =========================================================================
  // big_int_hex
  // =========================================================================
  test("big_int_hex") {
    assertEquals(Terser.minifyToString("0x20n\n0xfabn", noOpt), "0x20n;0xfabn;")
  }

  // =========================================================================
  // regression_big_int_hex_lower_with_e
  // =========================================================================
  test("regression_big_int_hex_lower_with_e") {
    assertEquals(Terser.minifyToString("0xaefen;", noOpt), "0xaefen;")
  }

  // =========================================================================
  // big_int_binary
  // =========================================================================
  test("big_int_binary") {
    assertEquals(Terser.minifyToString("0b101n", noOpt), "0b101n;")
  }

  // =========================================================================
  // big_int_octal
  // =========================================================================
  test("big_int_octal") {
    assertEquals(Terser.minifyToString("0o7n", noOpt), "0o7n;")
  }

  // =========================================================================
  // big_int_no_e — parse error
  // =========================================================================
  test("big_int_no_e") {
    val ex = intercept[JsParseError] {
      new Parser().parse("1e3n")
    }
    assert(
      ex.message.contains("Invalid or unexpected token") || ex.message.contains("Unexpected"),
      s"Expected error about invalid token, got: ${ex.getMessage}"
    )
  }

  // =========================================================================
  // big_int_bad_digits_for_base — parse error
  // =========================================================================
  test("big_int_bad_digits_for_base") {
    val ex = intercept[JsParseError] {
      new Parser().parse("0o9n")
    }
    assert(
      ex.message.contains("Invalid or unexpected token") || ex.message.contains("Unexpected"),
      s"Expected error about invalid token, got: ${ex.getMessage}"
    )
  }

  // =========================================================================
  // big_int_math — requires defaults:true compression
  // =========================================================================
  test("big_int_math".fail) {
    // Requires compression with defaults:true to fold BigInt arithmetic
    val input = "console.log({\n    sum: 10n + 15n,\n    exp: 2n ** 3n,\n    sub: 1n - 3n,\n    mul: 5n * 5n,\n    div: 15n / 5n,\n});"
    val result = Terser.minifyToString(input)
    assertEquals(result, "console.log({sum:25n,exp:8n,sub:-2n,mul:25n,div:3n});")
  }

  // =========================================================================
  // big_int_math_counter_examples — requires defaults:true compression
  // =========================================================================
  test("big_int_math_counter_examples".fail) {
    // Requires compression — these should NOT be folded (mixing types, bad operations)
    val input = "console.log({\n    mixing_types: 1 * 10n,\n    bad_shift: 1n >>> 0n,\n    bad_div: 1n / 0n,\n});"
    val result = Terser.minifyToString(input)
    assertEquals(result, "console.log({mixing_types:1*10n,bad_shift:1n>>>0n,bad_div:1n/0n});")
  }
}
