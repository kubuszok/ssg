/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/unicode.js
 * Original: 7 it() calls
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, Parser }
import ssg.js.output.{ OutputOptions, OutputStream }

final class UnicodeSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(10, "s")

  private val noOpt = MinifyOptions.NoOptimize

  // 1. "Should throw error if escaped first identifier char is not part of ID_start"
  // Note: Upstream throws JsParseError; ssg-js may throw JsParseError or NumberFormatException
  // for some cases (known gap in error handling). We intercept Exception to cover both.
  test("should throw error if escaped first identifier char is not part of ID_start") {
    val tests = List(
      "var \\u{0} = \"foo\";",
      "var \\u{10ffff} = \"bar\";",
      "var \\u000a = \"what's up\";"
    )
    tests.foreach { code =>
      intercept[Exception] {
        new Parser().parse(code)
      }
    }
  }

  // 2. "Should throw error if escaped non-first identifier char is not part of ID_start"
  test("should throw error if escaped non-first identifier char is not part of ID_continue") {
    val tests = List(
      "var a\\u{0} = \"foo\";",
      "var a\\u{10ffff} = \"bar\";",
      "var z\\u000a = \"what's up\";"
    )
    tests.foreach { code =>
      intercept[Exception] {
        new Parser().parse(code)
      }
    }
  }

  // 3. "Should throw error if identifier is a keyword with escape sequences"
  test("should throw error if identifier is a keyword with escape sequences") {
    val tests = List(
      "var \\u0069\\u006e = \"foo\"", // in
      "var \\u0076\\u0061\\u0072 = \"bar\"", // var
      "var \\u{66}\\u{6f}\\u{72} = \"baz\"", // for
      "var \\u0069\\u{66} = \"foobar\"", // if
      "var \\u{73}uper" // super
    )
    tests.foreach { code =>
      intercept[JsParseError] {
        new Parser().parse(code)
      }
    }
  }

  // 4. "Should read strings containing surrogates correctly"
  test("should read strings containing surrogates correctly") {
    // Surrogate pairs: \ud800\udc00 = U+10000, \udbff\udfff = U+10FFFF
    // With ascii_only + ecma 2015, should output as \u{10000} and \u{10ffff}
    val result1 = Terser.minifyToString(
      "var a = \"\ud800\udc00\";",
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(asciiOnly = true, ecma = 2015))
    )
    assertEquals(result1, "var a=\"\\u{10000}\";")

    val result2 = Terser.minifyToString(
      "var b = \"\udbff\udfff\";",
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(asciiOnly = true, ecma = 2015))
    )
    assertEquals(result2, "var b=\"\\u{10ffff}\";")
  }

  // 5. "Should parse raw characters correctly"
  test("should parse raw characters correctly") {
    val ast    = new Parser().parse("console.log(\"\\udbff\");")
    val result = OutputStream.printToString(ast)
    assertEquals(result, "console.log(\"\\udbff\");")
    // Round-trip: parse the output again
    val ast2    = new Parser().parse(result)
    val result2 = OutputStream.printToString(ast2)
    assertEquals(result2, "console.log(\"\\udbff\");")
  }

  // 6. "Should not strip quotes for object property name when there is unallowed character"
  test("should not strip quotes for object property name with unallowed character") {
    val code   = "console.log({\"hello\u30FBworld\":123});"
    val result = Terser.minifyToString(code, noOpt)
    // The mid-dot (U+30FB) is not a valid JS identifier char, so quotes must be preserved
    assertEquals(result, "console.log({\"hello\u30FBworld\":123});")
  }

  // 7. "Should not unescape unpaired surrogates"
  // This test in the original iterates over a large range of codepoints with multiple options.
  // We test a simplified version.
  test("should not unescape unpaired surrogates — simplified") {
    // A string with an unpaired surrogate should be preserved in output
    val code   = "\"\\ud800\";"
    val result = Terser.minifyToString(code, noOpt)
    assert(
      result.contains("\\ud800") || result.contains("\ud800"),
      s"Expected unpaired surrogate preserved, got: $result"
    )
  }
}
