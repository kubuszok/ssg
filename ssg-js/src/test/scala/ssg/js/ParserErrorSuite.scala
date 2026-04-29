/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Negative parser tests — inputs that should produce parse errors.
 * Ported from various terser/test/mocha/ files and terser/test/compress/ files.
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, Parser }

final class ParserErrorSuite extends munit.FunSuite {

  private def assertParseError(code: String, msgContains: String = ""): Unit = {
    val ex = intercept[JsParseError] { new Parser().parse(code) }
    if (msgContains.nonEmpty) {
      assert(
        ex.message.contains(msgContains),
        s"Expected error containing '$msgContains', got: ${ex.getMessage} for input: ${code.take(40)}"
      )
    }
  }

  // -- Syntax errors --

  test("unexpected token: missing closing paren") {
    assertParseError("function f(a{}", "Unexpected token")
  }

  test("unexpected token: missing closing brace") {
    assertParseError("function f() {", "Unexpected token")
  }

  test("unexpected token: extra closing paren") {
    assertParseError("var x = (1 + 2));", "Unexpected token")
  }

  test("duplicate label name") {
    assertParseError("L:{L:{}}", "Label L defined twice")
  }

  test("break outside loop or switch") {
    assertParseError("break;", "Break not inside a loop or switch")
  }

  test("continue outside loop") {
    assertParseError("continue;", "Continue not inside a loop or switch")
  }

  test("return outside function") {
    assertParseError("return 42;", "outside of function")
  }

  test("invalid assignment target") {
    assertParseError("1 = 2;")
  }

  // Known parser gap: multiple default clauses in switch not validated
  test("multiple default in switch".fail) {
    assertParseError("switch(x) { default: break; default: break; }", "More than one default clause")
  }

  // -- Strict mode errors --

  test("duplicate parameter in strict mode") {
    assertParseError("'use strict'; function f(a, a) {}")
  }

  // -- Destructuring errors --

  test("rest must be last in array destructuring") {
    assertParseError("let [...x, a] = o;", "Rest element must be last element")
  }

  test("rest must be last in function params") {
    assertParseError("function f(...a, b) {}", "Unexpected token")
  }

  // -- Generator errors --

  test("yield* must have expression") {
    assertParseError("function* g() { yield*; }")
  }

  test("yield with star on next line is error") {
    assertParseError("function* g() { yield\n*1; }")
  }

  // -- for-await errors --

  test("for-await outside async function") {
    assertParseError("for await (x of y) {}")
  }

  test("for-await with in instead of of") {
    assertParseError("async function f() { for await (x in y) {} }")
  }

  test("for-await with classic for syntax") {
    assertParseError("async function f() { for await (;;) {} }")
  }

  // -- Regex errors --

  test("unterminated regex") {
    assertParseError("/pattern", "Unterminated")
  }

  test("line terminator in regex") {
    assertParseError("/foo\n/")
  }

  // -- String errors --

  test("unterminated string literal") {
    assertParseError("var s = \"hello", "Unterminated")
  }

  test("unterminated template literal") {
    assertParseError("var s = `hello ${name", "Unexpected token")
  }

  // -- Unicode escapes in keywords --

  test("escaped keyword 'in'") {
    assertParseError("var \\u0069\\u006e = \"foo\"", "Escaped characters are not allowed in keywords")
  }

  test("escaped keyword 'var'") {
    assertParseError("var \\u0076\\u0061\\u0072 = \"bar\"", "Escaped characters are not allowed in keywords")
  }

  test("escaped keyword 'for'") {
    assertParseError("var \\u{66}\\u{6f}\\u{72} = \"baz\"", "Escaped characters are not allowed in keywords")
  }

  // -- let/const redeclaration (when scope analysis runs) --

  test("empty object pattern") {
    // Should parse without error
    val ast = new Parser().parse("var {} = foo;")
    assert(ast.body.nonEmpty)
  }

  test("empty array pattern") {
    // Should parse without error
    val ast = new Parser().parse("var [] = foo;")
    assert(ast.body.nonEmpty)
  }

  // -- Other edge cases --

  // Known parser gap: new.target outside function not validated
  test("new.target outside function".fail) {
    assertParseError("new.target;", "new.target")
  }

  // Known parser gap: super outside method not validated
  test("super outside method".fail) {
    assertParseError("super();")
  }

  test("import outside module context parses OK") {
    // import is valid at top level
    val ast = new Parser().parse("import x from 'y';")
    assert(ast.body.nonEmpty)
  }
}
