/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/expression.js
 * Original: 1 it() call
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, Parser }

final class ExpressionSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "Should not allow the first exponentiation operator to be prefixed with an unary operator"
  test("should not allow unary operator before exponentiation") {
    val tests = List(
      "+5 ** 3",
      "-5 ** 3",
      "~5 ** 3",
      "!5 ** 3",
      "void 5 ** 3",
      "typeof 5 ** 3",
      "delete 5 ** 3",
      "var a = -(5) ** 3;",
    )
    tests.foreach { code =>
      val ex = intercept[JsParseError] { parse(code) }
      assert(
        ex.message.matches("""Unexpected token: operator \((?:[!+~-]|void|typeof|delete)\).*"""),
        s"Expected 'Unexpected token: operator' error for: $code, got: ${ex.message}"
      )
    }
  }
}
