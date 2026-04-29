/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

import ssg.minify.js.{ JsMinifier, JsMinifyOptions }

final class JsMinifySuite extends munit.FunSuite {

  test("empty string") {
    assertEquals(JsMinifier.minify(""), "")
  }

  test("removes single-line comments") {
    val input  = "var x = 1; // comment\nvar y = 2;"
    val result = JsMinifier.minify(input)
    assert(!result.contains("// comment"), s"Expected comment removed, got: $result")
    assert(result.contains("var x"), s"Expected code preserved, got: $result")
    assert(result.contains("var y"), s"Expected code preserved, got: $result")
  }

  test("removes block comments") {
    val input  = "var x = 1; /* block comment */ var y = 2;"
    val result = JsMinifier.minify(input)
    assert(!result.contains("block comment"), s"Expected comment removed, got: $result")
    assert(result.contains("var x"), s"Expected code preserved, got: $result")
    assert(result.contains("var y"), s"Expected code preserved, got: $result")
  }

  test("removes multi-line block comments") {
    val input  = "var x = 1;\n/*\n * multi-line\n */\nvar y = 2;"
    val result = JsMinifier.minify(input)
    assert(!result.contains("multi-line"), s"Expected comment removed, got: $result")
  }

  test("collapses whitespace") {
    val input  = "var   x   =   1  ;"
    val result = JsMinifier.minify(input)
    assert(!result.contains("  "), s"Expected no double spaces, got: $result")
    assert(result.contains("var"), s"Expected code preserved, got: $result")
  }

  test("preserves single-quoted strings") {
    val input  = "var s = '// not a comment';"
    val result = JsMinifier.minify(input)
    assert(result.contains("'// not a comment'"), s"Expected string preserved, got: $result")
  }

  test("preserves double-quoted strings") {
    val input  = """var s = "/* not a comment */";"""
    val result = JsMinifier.minify(input)
    assert(result.contains("\"/* not a comment */\""), s"Expected string preserved, got: $result")
  }

  test("preserves template literals") {
    val input  = "var s = `hello // world`;"
    val result = JsMinifier.minify(input)
    assert(result.contains("`hello // world`"), s"Expected template preserved, got: $result")
  }

  test("preserves template literal expressions") {
    val input  = "var s = `${a + b} // text`;"
    val result = JsMinifier.minify(input)
    assert(result.contains("${a + b}"), s"Expected expression preserved, got: $result")
    assert(result.contains("// text"), s"Expected text inside template preserved, got: $result")
  }

  test("preserves escape sequences in strings") {
    val input  = """var s = "line1\nline2";"""
    val result = JsMinifier.minify(input)
    assert(result.contains("\"line1\\nline2\""), s"Expected escapes preserved, got: $result")
  }

  test("preserves regex literals") {
    val input  = "var r = /pattern/gi;"
    val result = JsMinifier.minify(input)
    assert(result.contains("/pattern/gi"), s"Expected regex preserved, got: $result")
  }

  test("preserves regex with character class containing /") {
    val input  = "var r = /[a/b]/g;"
    val result = JsMinifier.minify(input)
    assert(result.contains("/[a/b]/g"), s"Expected regex preserved, got: $result")
  }

  test("handles ES6 const and arrow functions") {
    val input  = "const  add  =  (a, b)  =>  a + b;"
    val result = JsMinifier.minify(input)
    assert(result.contains("const"), s"Expected const preserved, got: $result")
    assert(result.contains("=>"), s"Expected arrow preserved, got: $result")
  }

  test("handles ES6 class syntax") {
    val input =
      """class  Foo  extends  Bar  {
        |  constructor(x)  {
        |    super(x);
        |  }
        |}""".stripMargin
    val result = JsMinifier.minify(input)
    assert(result.contains("class"), s"Expected class keyword, got: $result")
    assert(result.contains("extends"), s"Expected extends keyword, got: $result")
    assert(result.contains("constructor"), s"Expected constructor, got: $result")
  }

  test("preserves newlines for ASI where needed") {
    val input  = "a\nb"
    val result = JsMinifier.minify(input)
    // Should keep a separator between identifiers
    assert(result.contains("a") && result.contains("b"), s"Expected both identifiers, got: $result")
    assert(result == "a\nb" || result == "a b", s"Expected separator between identifiers, got: $result")
  }

  test("removes leading and trailing whitespace") {
    val input  = "  var x = 1;  "
    val result = JsMinifier.minify(input)
    assertEquals(result.charAt(0), 'v')
    assert(result.last == ';', s"Expected trimmed, got: '$result'")
  }

  test("JsCompressor interface works") {
    val compressor: JsCompressor = JsMinifier
    val input  = "var x = 1; // comment"
    val result = compressor.compress(input)
    assert(!result.contains("// comment"), s"Expected comment removed via compress(), got: $result")
  }

  test("handles division vs regex") {
    val input  = "var x = a / b; // divide"
    val result = JsMinifier.minify(input)
    assert(result.contains("a / b") || result.contains("a/b"), s"Expected division preserved, got: $result")
    assert(!result.contains("// divide"), s"Expected comment removed, got: $result")
  }

  test("options to keep comments") {
    val input  = "var x = 1; // keep me"
    val opts   = JsMinifyOptions(removeComments = false)
    val result = JsMinifier.minify(input, opts)
    assert(result.contains("//"), s"Expected comment kept, got: $result")
    assert(result.contains("keep"), s"Expected comment text kept, got: $result")
  }

  test("already minified is stable") {
    val input  = "var x=1;var y=2;"
    val result = JsMinifier.minify(input)
    assertEquals(result, input)
  }
}
