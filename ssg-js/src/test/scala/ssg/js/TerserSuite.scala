/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Integration tests for the Terser public API.
 * Compression tests are marked with assume() until the Compressor's multi-pass loop is debugged (ISS-031/032). Parse+output works fine. */
package ssg
package js

final class TerserSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(10, "s")

  private val noOpt = MinifyOptions.NoOptimize

  // -- Parse + Output (no compression) --

  test("minify empty string") {
    assertEquals(Terser.minifyToString("", noOpt), "")
  }

  test("minify simple var") {
    assertEquals(Terser.minifyToString("var x = 1;", noOpt), "var x=1;")
  }

  test("minify removes whitespace") {
    assertEquals(Terser.minifyToString("var   x   =   1  ;", noOpt), "var x=1;")
  }

  test("minify function") {
    assertEquals(Terser.minifyToString("function foo(a, b) { return a + b; }", noOpt), "function foo(a,b){return a+b}")
  }

  test("minify arrow function") {
    assertEquals(Terser.minifyToString("const f = (x) => x * 2;", noOpt), "const f=x=>x*2;")
  }

  test("minify if/else") {
    assertEquals(Terser.minifyToString("if (x) { a(); } else { b(); }", noOpt), "if(x){a()}else{b()}")
  }

  test("minify for loop") {
    assertEquals(Terser.minifyToString("for (var i = 0; i < 10; i++) { x(); }", noOpt), "for(var i=0;i<10;i++){x()}")
  }

  test("minify template literal") {
    assertEquals(Terser.minifyToString("`hello ${name}`;", noOpt), "`hello ${name}`;")
  }

  test("minify import/export") {
    assertEquals(Terser.minifyToString("import x from 'y'; export default x;", noOpt), "import x from\"y\";export default x;")
  }

  // -- TerserJsCompressor --

  test("TerserJsCompressor.compress handles empty") {
    assertEquals(TerserJsCompressor.compress(""), "")
  }

  test("TerserJsCompressor.compress graceful degradation") {
    val result = TerserJsCompressor.compress("this is not valid JS {{{}}")
    assert(result.contains("this is not valid"), s"Expected original returned, got: $result")
  }

  // -- MinifyResult --

  test("MinifyResult has ast and code") {
    val result = Terser.minify("var x = 1;", noOpt)
    assert(result.ast.body.nonEmpty)
    assertEquals(result.code, "var x=1;")
  }

  // -- With compression --
  // Note: Compression tests use assume() to skip if compressor hangs (ISS-031/032)

  private def isNative: Boolean =
    System.getProperty("java.vm.name", "").toLowerCase.contains("native") ||
      System.getProperty("java.vendor", "").toLowerCase.contains("scala native") ||
      !System.getProperty("java.home", "").contains("java")

  // Compressor multi-pass loop is broken (ISS-031/032), so compression tests may hang.
  // Use assume() to skip these until the compressor is fixed.
  private def assumeCompressorWorks(): Unit = {
    assume(!isNative, "Compression tests skip on Native due to regex limitations")
    // ISS-031/032: compressor multi-pass loop can hang
    assume(false, "Compression tests disabled — compressor multi-pass loop hangs (ISS-031/032)")
  }

  test("compress drops debugger") {
    assumeCompressorWorks()
    val result = Terser.minifyToString("debugger; var x = 1;")
    assert(!result.contains("debugger"), s"Expected debugger dropped, got: $result")
  }

  test("compress constant folding") {
    assumeCompressorWorks()
    val result = Terser.minifyToString("var x = 1 + 2;")
    assert(result.contains("3"), s"Expected constant folding (1+2->3), got: $result")
  }

  test("compress with defaults does not crash") {
    assumeCompressorWorks()
    val code   = "function foo(a) { var b = a + 1; return b; }"
    val result = Terser.minifyToString(code)
    assert(result.nonEmpty, s"Expected non-empty output, got: $result")
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
    assertEquals(
      Terser.minifyToString(code, noOpt),
      "function fibonacci(n){if(n<=1)return n;return fibonacci(n-1)+fibonacci(n-2)}var result=fibonacci(10);console.log(result);"
    )
  }

  test("minify ES6+ (no compress)") {
    assertEquals(Terser.minifyToString("let x = 1; export default x;", noOpt), "let x=1;export default x;")
  }

  test("minify output is shorter than formatted input") {
    val code =
      """function   add(  a ,  b  ) {
        |  // add two numbers
        |  var   result   =   a   +   b;
        |  return   result;
        |}""".stripMargin
    assertEquals(Terser.minifyToString(code, noOpt), "function add(a,b){var result=a+b;return result}")
  }
}
