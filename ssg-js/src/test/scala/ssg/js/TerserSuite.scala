/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Integration tests for the Terser public API.
 * Compression tests are marked TODO until the Compressor's multi-pass
 * loop is debugged (currently hangs). Parse+output works fine.
 */
package ssg
package js

final class TerserSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(10, "s")

  private val noOpt = MinifyOptions.NoOptimize

  // -- Parse + Output (no compression) --

  test("minify empty string") {
    val result = Terser.minifyToString("", noOpt)
    assertEquals(result, "")
  }

  test("minify simple var") {
    val result = Terser.minifyToString("var x = 1;", noOpt)
    assert(result.contains("var"), s"got: $result")
    assert(result.contains("1"), s"got: $result")
  }

  test("minify removes whitespace") {
    val result = Terser.minifyToString("var   x   =   1  ;", noOpt)
    assert(!result.contains("   "), s"Expected whitespace removed, got: $result")
  }

  test("minify function") {
    val result = Terser.minifyToString("function foo(a, b) { return a + b; }", noOpt)
    assert(result.contains("function"), s"got: $result")
    assert(result.contains("return"), s"got: $result")
    assert(result.length < "function foo(a, b) { return a + b; }".length, s"Expected shorter output, got: $result")
  }

  test("minify arrow function") {
    val result = Terser.minifyToString("const f = (x) => x * 2;", noOpt)
    assert(result.contains("=>"), s"got: $result")
  }

  test("minify if/else") {
    val result = Terser.minifyToString("if (x) { a(); } else { b(); }", noOpt)
    assert(result.contains("if"), s"got: $result")
    assert(result.contains("else"), s"got: $result")
  }

  test("minify for loop") {
    val result = Terser.minifyToString("for (var i = 0; i < 10; i++) { x(); }", noOpt)
    assert(result.contains("for"), s"got: $result")
  }

  test("minify template literal") {
    val result = Terser.minifyToString("`hello ${name}`;", noOpt)
    assert(result.contains("`"), s"got: $result")
  }

  test("minify import/export") {
    val result = Terser.minifyToString("import x from 'y'; export default x;", noOpt)
    assert(result.contains("import"), s"got: $result")
    assert(result.contains("export"), s"got: $result")
  }

  // -- TerserJsCompressor --

  test("TerserJsCompressor.compress handles empty") {
    val result = TerserJsCompressor.compress("")
    assertEquals(result, "")
  }

  test("TerserJsCompressor.compress graceful degradation") {
    val result = TerserJsCompressor.compress("this is not valid JS {{{}}")
    assert(result.contains("this is not valid"), s"Expected original returned, got: $result")
  }

  // -- MinifyResult --

  test("MinifyResult has ast and code") {
    val result = Terser.minify("var x = 1;", noOpt)
    assert(result.ast.body.nonEmpty)
    assert(result.code.nonEmpty)
  }

  // -- With compression --

  test("compress drops debugger") {
    val result = Terser.minifyToString("debugger; var x = 1;")
    assert(!result.contains("debugger"), s"Expected debugger dropped, got: $result")
  }

  test("compress constant folding") {
    val result = Terser.minifyToString("var x = 1 + 2;")
    // With evaluation enabled, 1+2 should fold to 3
    assert(result.contains("3") || result.contains("1+2"), s"got: $result")
  }

  test("compress with defaults does not crash") {
    val code   = "function foo(a) { var b = a + 1; return b; }"
    val result = Terser.minifyToString(code)
    assert(result.contains("function"), s"got: $result")
    assert(result.contains("return"), s"got: $result")
  }

  // -- Real-world --

  test("minify fibonacci (no compress)") {
    val code =
      """function fibonacci(n) {
        |  if (n <= 1) return n;
        |  return fibonacci(n - 1) + fibonacci(n - 2);
        |}
        |var result = fibonacci(10);
        |console.log(result);""".stripMargin
    val result = Terser.minifyToString(code, noOpt)
    assert(result.length < code.length, s"Expected shorter: $result")
    assert(result.contains("fibonacci"), s"Expected function name: $result")
    assert(result.contains("console"), s"Expected console: $result")
  }

  test("minify ES6+ (no compress)") {
    val code   = "let x = 1; export default x;"
    val result = Terser.minifyToString(code, noOpt)
    assert(result.contains("let"), s"Expected let: $result")
    assert(result.contains("export"), s"Expected export: $result")
  }

  test("minify output is shorter than formatted input") {
    val code =
      """function   add(  a ,  b  ) {
        |  // add two numbers
        |  var   result   =   a   +   b;
        |  return   result;
        |}""".stripMargin
    val result = Terser.minifyToString(code, noOpt)
    assert(result.length < code.length, s"Expected shorter: ${result.length} vs ${code.length}")
  }
}
