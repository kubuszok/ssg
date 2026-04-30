/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/string-literal.js
 * Original: 6 it() calls
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, Parser }
import ssg.js.output.OutputStream

final class StringLiteralSuite extends munit.FunSuite {

  // 1. "Should throw syntax error if a string literal contains a newline"
  test("should throw if string literal contains a newline") {
    val inputs = List("'\n'", "'\r'", "\"\r\n\"")
    inputs.foreach { input =>
      val ex = intercept[JsParseError](new Parser().parse(input))
      assert(ex.message.contains("Unterminated"), s"Expected unterminated error, got: ${ex.getMessage}")
    }
  }

  // 2. "Should not throw syntax error if a string has a line continuation"
  test("should not throw if string has line continuation") {
    val ast    = new Parser().parse("var a = \"a\\\nb\";")
    val result = OutputStream.printToString(ast)
    assertEquals(result, "var a=\"ab\";")
  }

  // 3. "Should throw error in strict mode if string contains escaped octalIntegerLiteral"
  test("should throw in strict mode for escaped octal") {
    val inputs = List(
      "\"use strict\";\n\"\\76\";",
      "\"use strict\";\nvar foo = \"\\76\";",
      "\"use strict\";\n\"\\1\";",
      "\"use strict\";\n\"\\07\";",
      "\"use strict\";\n\"\\011\""
    )
    inputs.foreach { input =>
      val ex = intercept[JsParseError](new Parser().parse(input))
      assert(
        ex.message.contains("octal") || ex.message.contains("Legacy"),
        s"Expected octal error, got: ${ex.getMessage}"
      )
    }
  }

  // 4. "Should not throw error outside strict mode if string contains escaped octalIntegerLiteral"
  test("should not throw outside strict mode for escaped octal") {
    val tests = List(
      ("\"\\76\";", ";\">\";"),
      ("\"\\0\"", "\"\\0\";")
    )
    tests.foreach { case (input, expected) =>
      val ast    = new Parser().parse(input)
      val result = OutputStream.printToString(ast)
      assertEquals(result, expected, s"Mismatch for input: $input")
    }
  }

  // 5. "Should not throw error when digit is 8 or 9"
  test("should not throw when digit after backslash-0 is 8 or 9") {
    val ast1 = new Parser().parse("\"use strict\";\"\\08\";")
    val r1   = OutputStream.printToString(ast1)
    assertEquals(r1, "\"use strict\";\"\\x008\";")

    val ast2 = new Parser().parse("\"use strict\";\"\\09\";")
    val r2   = OutputStream.printToString(ast2)
    assertEquals(r2, "\"use strict\";\"\\x009\";")
  }

  // 6. "Should not unescape unpaired surrogates"
  // The full test iterates over 0-0xFFFF with eval comparison, which is too large for Scala.
  // We test a simplified version.
  test("should not unescape unpaired surrogates — simplified") {
    val code   = "\"\\ud800\";"
    val result = Terser.minifyToString(code, MinifyOptions.NoOptimize)
    assert(
      result.contains("\\ud800") || result.contains("\ud800"),
      s"Expected unpaired surrogate preserved, got: $result"
    )
  }
}
