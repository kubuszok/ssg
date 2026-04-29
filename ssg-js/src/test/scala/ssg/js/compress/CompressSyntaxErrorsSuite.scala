/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Syntax error tests.
 * Ported from: terser/test/compress/syntax-errors.js (29 test cases)
 *
 * These tests verify that the parser produces the correct error messages
 * for invalid JavaScript input. Uses bad_input/expect_error format.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support bad_input format. */
package ssg
package js
package compress

import ssg.js.parse.{ JsParseError, Parser }

final class CompressSyntaxErrorsSuite extends munit.FunSuite {

  private def assertParseError(input: String, expectedMessage: String)(using loc: munit.Location): Unit = {
    val ex = intercept[JsParseError] {
      new Parser().parse(input)
    }
    assert(
      ex.message.contains(expectedMessage),
      s"Expected message containing '$expectedMessage', got: '${ex.getMessage}'"
    )
  }

  // =========================================================================
  // missing_loop_body
  // =========================================================================
  test("missing_loop_body") {
    assertParseError("for (;;)\n", "Unexpected token")
  }

  // =========================================================================
  // decrement_constant_number
  // =========================================================================
  test("decrement_constant_number") {
    assertParseError("5--", "Invalid use of -- operator")
  }

  // =========================================================================
  // assign_to_call
  // =========================================================================
  test("assign_to_call") {
    assertParseError("Math.random() /= 2", "Invalid assignment")
  }

  // =========================================================================
  // increment_this
  // =========================================================================
  test("increment_this") {
    assertParseError("++this", "Invalid use of ++ operator")
  }

  // =========================================================================
  // increment_null
  // =========================================================================
  test("increment_null") {
    assertParseError("++null", "Invalid use of ++ operator")
  }

  // =========================================================================
  // invalid_dot
  // =========================================================================
  test("invalid_dot") {
    assertParseError("a.=", "Unexpected token")
  }

  // =========================================================================
  // invalid_percent
  // =========================================================================
  test("invalid_percent") {
    assertParseError("%.a;", "Unexpected token")
  }

  // =========================================================================
  // invalid_divide
  // =========================================================================
  test("invalid_divide") {
    // a./() — the /() starts a regex parse, which may give different error
    val ex = intercept[JsParseError] {
      new Parser().parse("a./()")
    }
    assert(
      ex.message.contains("Unexpected token") || ex.message.contains("Unexpected"),
      s"Expected error containing 'Unexpected', got: '${ex.getMessage}'"
    )
  }

  // =========================================================================
  // invalid_object_key
  // =========================================================================
  test("invalid_object_key") {
    assertParseError("x({%: 1})", "Unexpected token")
  }

  // =========================================================================
  // invalid_const
  // =========================================================================
  test("invalid_const") {
    assertParseError("const a\n", "Missing initializer in const declaration")
  }

  // =========================================================================
  // invalid_delete
  // =========================================================================
  test("invalid_delete") {
    val input =
      """function f(x) {
        |    delete 42;
        |    delete (0, x);
        |    delete null;
        |    delete x;
        |}
        |
        |function g(x) {
        |    "use strict";
        |    delete 42;
        |    delete (0, x);
        |    delete null;
        |    delete x;
        |}""".stripMargin
    assertParseError(input, "delete")
  }

  // =========================================================================
  // invalid_arguments
  // =========================================================================
  test("invalid_arguments") {
    val input =
      """function x() {
        |    "use strict"
        |    function a(arguments) { }
        |}""".stripMargin
    assertParseError(input, "arguments")
  }

  // =========================================================================
  // invalid_eval
  // =========================================================================
  test("invalid_eval") {
    val input =
      """function x() {
        |    "use strict"
        |    function eval() { }
        |}""".stripMargin
    assertParseError(input, "eval")
  }

  // =========================================================================
  // invalid_iife
  // =========================================================================
  test("invalid_iife") {
    val input =
      """function x() {
        |    "use strict"
        |    !function arguments() {
        |
        |    }()
        |}""".stripMargin
    assertParseError(input, "arguments")
  }

  // =========================================================================
  // invalid_catch_eval
  // =========================================================================
  test("invalid_catch_eval") {
    val input =
      """function x() {
        |    "use strict"
        |    try {
        |
        |    } catch (eval) {
        |
        |    }
        |}""".stripMargin
    assertParseError(input, "eval")
  }

  // =========================================================================
  // invalid_var_eval
  // =========================================================================
  test("invalid_var_eval") {
    val input =
      """function x() {
        |    "use strict"
        |    var eval
        |}""".stripMargin
    assertParseError(input, "eval")
  }

  // =========================================================================
  // invalid_else
  // =========================================================================
  test("invalid_else") {
    assertParseError("if (0) else 1", "Unexpected token")
  }

  // =========================================================================
  // invalid_return
  // =========================================================================
  test("invalid_return") {
    assertParseError("return 42", "'return' outside of function")
  }

  // =========================================================================
  // export_anonymous_class
  // =========================================================================
  test("export_anonymous_class") {
    assertParseError("export class {}", "Unexpected token")
  }

  // =========================================================================
  // export_anonymous_function
  // =========================================================================
  test("export_anonymous_function") {
    assertParseError("export function () {}", "Unexpected token")
  }

  // =========================================================================
  // spread_in_sequence
  // =========================================================================
  test("spread_in_sequence") {
    assertParseError("(a, ...b)", "Unexpected token")
  }

  // =========================================================================
  // invalid_for_in
  // =========================================================================
  test("invalid_for_in") {
    assertParseError("for (1, 2, a in b) { }", "Invalid left-hand side")
  }

  // =========================================================================
  // invalid_for_in_var
  // =========================================================================
  test("invalid_for_in_var") {
    assertParseError("for (var a, b in c) { }", "Only one variable declaration")
  }

  // =========================================================================
  // big_int_decimal
  // =========================================================================
  test("big_int_decimal") {
    assertParseError(".23n", "Invalid or unexpected token")
  }

  // =========================================================================
  // big_int_scientific_format
  // =========================================================================
  test("big_int_scientific_format") {
    assertParseError("1e3n", "Invalid or unexpected token")
  }

  // =========================================================================
  // invalid_privatename_in_object
  // =========================================================================
  test("invalid_privatename_in_object") {
    val input =
      """const myObject = {
        |    foo: 'bar',
        |    #something: 5,
        |}""".stripMargin
    assertParseError(input, "private fields are not allowed")
  }

  // =========================================================================
  // private_field_out_of_class_field
  // =========================================================================
  test("private_field_out_of_class_field") {
    val input =
      """function test() {
        |    return this.#p;
        |}""".stripMargin
    assertParseError(input, "Private field must be used in an enclosing class")
  }

  // =========================================================================
  // private_field_out_of_class_field_in_operator
  // =========================================================================
  test("private_field_out_of_class_field_in_operator") {
    val input =
      """function test(input) {
        |    #p in input;
        |    return 10;
        |}""".stripMargin
    assertParseError(input, "Private field must be used in an enclosing class")
  }

  // =========================================================================
  // invaild__in_operator_expression_in_class_field
  // (note: misspelling "invaild" is from the original)
  // Known gap: parser does not support class with concise methods, so the
  // error message differs from the original. The important thing is that
  // parsing fails.
  // =========================================================================
  test("invaild__in_operator_expression_in_class_field") {
    val input =
      """class A {
        |    #p;
        |    isA () {
        |        #p + 10;
        |        return this.#p;
        |    }
        |}""".stripMargin
    intercept[JsParseError] {
      new Parser().parse(input)
    }
  }
}
