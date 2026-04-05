/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.Parser
import ssg.js.output.{ OutputOptions, OutputStream }

final class OutputSuite extends munit.FunSuite {

  private def parse(code: String): AstToplevel = new Parser().parse(code)

  private def generate(ast: AstNode, opts: OutputOptions = OutputOptions()): String = {
    val out = new OutputStream(opts)
    out.printNode(ast)
    out.get()
  }

  private def minify(code: String): String = generate(parse(code))

  // -- Basic round-trip --

  test("var declaration") {
    val result = minify("var x = 1;")
    assert(result.contains("var"), s"got: $result")
    assert(result.contains("x"), s"got: $result")
    assert(result.contains("1"), s"got: $result")
  }

  test("let and const") {
    val result = minify("let a = 1; const b = 2;")
    assert(result.contains("let"), s"got: $result")
    assert(result.contains("const"), s"got: $result")
  }

  test("function declaration") {
    val result = minify("function foo(a, b) { return a + b; }")
    assert(result.contains("function"), s"got: $result")
    assert(result.contains("foo"), s"got: $result")
    assert(result.contains("return"), s"got: $result")
  }

  test("arrow function") {
    val result = minify("var f = (x) => x * 2;")
    assert(result.contains("=>"), s"got: $result")
  }

  test("if/else") {
    val result = minify("if (x) { a(); } else { b(); }")
    assert(result.contains("if"), s"got: $result")
    assert(result.contains("else"), s"got: $result")
  }

  test("for loop") {
    val result = minify("for (var i = 0; i < 10; i++) { x(); }")
    assert(result.contains("for"), s"got: $result")
  }

  test("while loop") {
    val result = minify("while (x) { y(); }")
    assert(result.contains("while"), s"got: $result")
  }

  test("try/catch") {
    val result = minify("try { x(); } catch (e) { y(); }")
    assert(result.contains("try"), s"got: $result")
    assert(result.contains("catch"), s"got: $result")
  }

  test("switch") {
    val result = minify("switch (x) { case 1: a(); break; default: b(); }")
    assert(result.contains("switch"), s"got: $result")
    assert(result.contains("case"), s"got: $result")
  }

  // -- Expressions --

  test("binary expression") {
    val result = minify("a + b * c;")
    assert(result.contains("+"), s"got: $result")
    assert(result.contains("*"), s"got: $result")
  }

  test("function call") {
    val result = minify("foo(1, 2, 3);")
    assert(result.contains("foo"), s"got: $result")
    assert(result.contains("1"), s"got: $result")
  }

  test("member access") {
    val result = minify("a.b.c;")
    assert(result.contains("a.b.c"), s"got: $result")
  }

  test("ternary") {
    val result = minify("x ? a : b;")
    assert(result.contains("?"), s"got: $result")
    assert(result.contains(":"), s"got: $result")
  }

  test("assignment") {
    val result = minify("x = 1;")
    assert(result.contains("x=1") || result.contains("x = 1"), s"got: $result")
  }

  // -- Literals --

  test("string literal") {
    val result = minify("1; 'hello';")
    assert(result.contains("hello"), s"got: $result")
  }

  test("number literal") {
    val result = minify("42;")
    assert(result.contains("42"), s"got: $result")
  }

  test("boolean literals") {
    val result = minify("true; false;")
    assert(result.contains("true"), s"got: $result")
    assert(result.contains("false"), s"got: $result")
  }

  test("null") {
    val result = minify("null;")
    assert(result.contains("null"), s"got: $result")
  }

  test("array") {
    val result = minify("[1, 2, 3];")
    assert(result.contains("[") && result.contains("]"), s"got: $result")
  }

  test("object") {
    val result = minify("({a: 1, b: 2});")
    assert(result.contains("a") && result.contains("b"), s"got: $result")
  }

  // -- ES6+ --

  test("template literal") {
    val result = minify("`hello ${name} world`;")
    assert(result.contains("`"), s"got: $result")
  }

  test("class") {
    val result = minify("class Foo extends Bar { }")
    assert(result.contains("class"), s"got: $result")
    assert(result.contains("Foo"), s"got: $result")
    assert(result.contains("extends"), s"got: $result")
  }

  test("import") {
    val result = minify("import foo from 'bar';")
    assert(result.contains("import"), s"got: $result")
  }

  test("export default") {
    val result = minify("export default 42;")
    assert(result.contains("export"), s"got: $result")
  }

  // -- Beautify mode --

  test("beautify produces readable output") {
    val code   = "function foo(a){return a+1}"
    val result = generate(parse(code), OutputOptions(beautify = true))
    // Beautified should have newlines and indentation
    assert(result.contains("\n"), s"Expected newlines in beautified output: $result")
  }

  // -- Real-world --

  test("real-world fibonacci") {
    val code =
      """function fibonacci(n) {
        |  if (n <= 1) return n;
        |  return fibonacci(n - 1) + fibonacci(n - 2);
        |}""".stripMargin
    val result = minify(code)
    assert(result.contains("fibonacci"), s"got: $result")
    assert(result.contains("return"), s"got: $result")
  }

  test("minified output is shorter than input") {
    val code =
      """function   add(  a ,  b  ) {
        |  // add two numbers
        |  var   result   =   a   +   b;
        |  return   result;
        |}""".stripMargin
    val result = minify(code)
    assert(result.length < code.length, s"Expected minified to be shorter: ${result.length} vs ${code.length}")
  }
}
