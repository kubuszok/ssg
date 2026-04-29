/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/eof.js
 * Original: 1 it() call
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, Parser }

final class EofSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "Should test code for at least throwing syntax error when incomplete"
  test("should throw syntax error for incomplete code") {
    def testEol(input: String, chopLimit: Int = -1): Unit = {
      val limit = if (chopLimit < 0) input.length - 1 else chopLimit

      // Full input should parse fine
      parse(input)

      // Chop off characters from the end — each should throw
      var remaining = limit
      var i = input.length - 1
      while (remaining > 0) {
        val code = input.substring(0, i)
        intercept[JsParseError] { parse(code) }
        remaining -= 1
        i -= 1
      }
    }

    testEol("var \\u1234", 7) // Incomplete identifier
    testEol("'Incomplete string'")
    testEol("/Unterminated regex/")
    testEol("` Unterminated template string`")
    testEol("/* Unfinishing multiline comment */")
  }
}
