/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Parse+output tests for wrap_iife output option.
 * Ported from: terser/test/compress/wrap_iife.js (3 test cases)
 *
 * These tests verify the wrap_iife beautify option wraps IIFE expressions.
 * Tests use CompressTestHelper.AllOff with negateIife=false to match the original.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support beautify format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.output.OutputOptions

final class CompressWrapIifeSuite extends munit.FunSuite {

  // wrap_iife tests use negate_iife:false in compress + wrap_iife:true in output
  private val wrapOpts = MinifyOptions(
    compress = false,
    mangle = false,
    output = OutputOptions(wrapIife = true)
  )

  // =========================================================================
  // wrap_iife
  // =========================================================================
  test("wrap_iife") {
    val input  = "(function() {\n    return function() {\n        console.log('test')\n    };\n})()();"
    val result = Terser.minifyToString(input, wrapOpts)
    assertEquals(result, "(function(){return function(){console.log(\"test\")}})()();")
  }

  // =========================================================================
  // wrap_iife_in_expression
  // =========================================================================
  test("wrap_iife_in_expression") {
    val input  = "foo = (function () {\n    return bar();\n})();"
    val result = Terser.minifyToString(input, wrapOpts)
    assertEquals(result, "foo=(function(){return bar()})();")
  }

  // =========================================================================
  // wrap_iife_in_return_call
  // =========================================================================
  test("wrap_iife_in_return_call") {
    val input  = "(function() {\n    return (function() {\n        console.log('test')\n    })();\n})()();"
    val result = Terser.minifyToString(input, wrapOpts)
    assertEquals(result, "(function(){return(function(){console.log(\"test\")})()})()();")
  }
}
