/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Tests for the OutputStream code generator.
 * All assertions use assertEquals with exact expected output. */
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
    assertEquals(minify("var x = 1;"), "var x=1;")
  }

  test("let and const") {
    assertEquals(minify("let a = 1; const b = 2;"), "let a=1;const b=2;")
  }

  test("function declaration") {
    assertEquals(minify("function foo(a, b) { return a + b; }"), "function foo(a,b){return a+b}")
  }

  test("arrow function") {
    assertEquals(minify("var f = (x) => x * 2;"), "var f=x=>x*2;")
  }

  test("if/else") {
    assertEquals(minify("if (x) { a(); } else { b(); }"), "if(x){a()}else{b()}")
  }

  test("for loop") {
    assertEquals(minify("for (var i = 0; i < 10; i++) { x(); }"), "for(var i=0;i<10;i++){x()}")
  }

  test("while loop") {
    assertEquals(minify("while (x) { y(); }"), "while(x){y()}")
  }

  test("try/catch") {
    assertEquals(minify("try { x(); } catch (e) { y(); }"), "try{x()}catch(e){y()}")
  }

  test("switch") {
    assertEquals(minify("switch (x) { case 1: a(); break; default: b(); }"), "switch(x){case 1:a();break;default:b()}")
  }

  // -- Expressions --

  test("binary expression") {
    assertEquals(minify("a + b * c;"), "a+b*c;")
  }

  test("function call") {
    assertEquals(minify("foo(1, 2, 3);"), "foo(1,2,3);")
  }

  test("member access") {
    assertEquals(minify("a.b.c;"), "a.b.c;")
  }

  test("ternary") {
    assertEquals(minify("x ? a : b;"), "x?a:b;")
  }

  test("assignment") {
    assertEquals(minify("x = 1;"), "x=1;")
  }

  // -- Literals --

  test("string literal") {
    assertEquals(minify("1; 'hello';"), "1;\"hello\";")
  }

  test("number literal") {
    assertEquals(minify("42;"), "42;")
  }

  test("boolean literals") {
    assertEquals(minify("true; false;"), "true;false;")
  }

  test("null") {
    assertEquals(minify("null;"), "null;")
  }

  test("array") {
    assertEquals(minify("[1, 2, 3];"), "[1,2,3];")
  }

  test("object") {
    assertEquals(minify("({a: 1, b: 2});"), "{a:1,b:2};")
  }

  // -- ES6+ --

  test("template literal") {
    assertEquals(minify("`hello ${name} world`;"), "`hello ${name} world`;")
  }

  test("class") {
    assertEquals(minify("class Foo extends Bar { }"), "class Foo extends Bar{}")
  }

  test("import") {
    assertEquals(minify("import foo from 'bar';"), "import foo from\"bar\";")
  }

  test("export default") {
    assertEquals(minify("export default 42;"), "export default 42;")
  }

  // -- Beautify mode --

  test("beautify produces readable output") {
    val code   = "function foo(a){return a+1}"
    val result = generate(parse(code), OutputOptions(beautify = true))
    assertEquals(result, "function foo(a) {\n    return a + 1;\n}")
  }

  // -- Real-world --

  test("real-world fibonacci") {
    val code =
      """function fibonacci(n) {
        |  if (n <= 1) return n;
        |  return fibonacci(n - 1) + fibonacci(n - 2);
        |}""".stripMargin
    assertEquals(minify(code), "function fibonacci(n){if(n<=1)return n;return fibonacci(n-1)+fibonacci(n-2)}")
  }

  test("minified output is shorter than input") {
    val code =
      """function   add(  a ,  b  ) {
        |  // add two numbers
        |  var   result   =   a   +   b;
        |  return   result;
        |}""".stripMargin
    assertEquals(minify(code), "function add(a,b){var result=a+b;return result}")
  }
}
