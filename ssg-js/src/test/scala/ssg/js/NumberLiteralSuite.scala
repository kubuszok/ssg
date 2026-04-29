/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/number-literal.js
 * Original: 1 it() call
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, Parser }

final class NumberLiteralSuite extends munit.FunSuite {

  // 1. "Should not allow legacy octal literals in strict mode"
  test("should not allow legacy octal literals in strict mode") {
    val inputs = List(
      "\"use strict\";00;",
      "\"use strict\"; var foo = 00;",
    )
    inputs.foreach { input =>
      val ex = intercept[JsParseError] { new Parser().parse(input) }
      assert(
        ex.message.contains("octal") || ex.message.contains("Legacy"),
        s"Expected octal error, got: ${ex.getMessage} for input: $input"
      )
    }
  }
}
