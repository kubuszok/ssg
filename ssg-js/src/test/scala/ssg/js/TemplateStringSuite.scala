/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/template-string.js
 * Original: 3 it() calls
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, Parser }
import ssg.js.output.OutputStream

final class TemplateStringSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "Should not accept invalid sequences"
  test("should not accept invalid template string sequences") {
    val tests = List(
      // Stress invalid expression
      "var foo = `Hello ${]}`",
      "var foo = `Test 123 ${>}`",
      "var foo = `Blah ${;}`",
      // Stress invalid template_cont after expression
      "var foo = `Blablabla ${123 456}`",
      "var foo = `Blub ${123;}`",
      "var foo = `Bleh ${a b}`"
    )
    tests.foreach { code =>
      val ex = intercept[JsParseError](parse(code))
      assert(
        ex.message.startsWith("Unexpected token: "),
        s"Expected 'Unexpected token' error for: $code, got: ${ex.message}"
      )
    }
  }

  // 2. "Should process all line terminators as LF"
  test("should process all line terminators as LF") {
    val tests = List(
      "`a\rb`",
      "`a\nb`",
      "`a\r\nb`"
    )
    tests.foreach { code =>
      assertEquals(OutputStream.printToString(parse(code)), "`a\\nb`;")
    }
  }

  // 3. "Should not throw on extraneous escape (#231)"
  test("should not throw on extraneous escape") {
    // Should parse without throwing
    parse("`\\a`")
  }
}
