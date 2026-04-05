/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package minify

import ssg.minify.css.{ CssMinifier, CssMinifyOptions }

final class CssMinifySuite extends munit.FunSuite {

  test("empty string") {
    assertEquals(CssMinifier.minify(""), "")
  }

  test("removes block comments") {
    val input = "body { /* color */ color: red; }"
    assertEquals(CssMinifier.minify(input), "body{color:red}")
  }

  test("removes multi-line comments") {
    val input =
      """/*
        | * Multi-line comment
        | */
        |body { color: red; }""".stripMargin
    assertEquals(CssMinifier.minify(input), "body{color:red}")
  }

  test("collapses whitespace around braces") {
    val input = "body  {  color : red  }"
    assertEquals(CssMinifier.minify(input), "body{color:red}")
  }

  test("collapses whitespace around colons") {
    val input = "a { color : blue ; font-size : 14px }"
    assertEquals(CssMinifier.minify(input), "a{color:blue;font-size:14px}")
  }

  test("collapses whitespace around semicolons") {
    val input = "a { color: red ; margin: 0 ; }"
    assertEquals(CssMinifier.minify(input), "a{color:red;margin:0}")
  }

  test("collapses whitespace around commas") {
    val input = "h1 , h2 , h3 { color: red }"
    assertEquals(CssMinifier.minify(input), "h1,h2,h3{color:red}")
  }

  test("removes trailing semicolons") {
    val input = "a{color:red;margin:0;}"
    assertEquals(CssMinifier.minify(input), "a{color:red;margin:0}")
  }

  test("removes empty rules") {
    val input = "a{color:red}b{}c{margin:0}"
    assertEquals(CssMinifier.minify(input), "a{color:red}c{margin:0}")
  }

  test("shortens 6-digit hex to 3-digit") {
    val input = "a{color:#ffffff}"
    assertEquals(CssMinifier.minify(input), "a{color:#fff}")
  }

  test("shortens mixed case hex colors") {
    val input = "a{color:#AABBCC}"
    assertEquals(CssMinifier.minify(input), "a{color:#ABC}")
  }

  test("does not shorten non-matching hex colors") {
    val input = "a{color:#abcdef}"
    assertEquals(CssMinifier.minify(input), "a{color:#abcdef}")
  }

  test("collapses 0px to 0") {
    val input = "a{margin:0px}"
    assertEquals(CssMinifier.minify(input), "a{margin:0}")
  }

  test("collapses 0em to 0") {
    val input = "a{padding:0em}"
    assertEquals(CssMinifier.minify(input), "a{padding:0}")
  }

  test("collapses 0rem to 0") {
    val input = "a{margin:0rem}"
    assertEquals(CssMinifier.minify(input), "a{margin:0}")
  }

  test("collapses 0% to 0") {
    val input = "a{width:0%}"
    assertEquals(CssMinifier.minify(input), "a{width:0}")
  }

  test("does not collapse non-zero values with units") {
    val input = "a{margin:10px}"
    assertEquals(CssMinifier.minify(input), "a{margin:10px}")
  }

  test("does not collapse 0 in decimal like 10px") {
    val input = "a{margin:10px;padding:20em}"
    assertEquals(CssMinifier.minify(input), "a{margin:10px;padding:20em}")
  }

  test("preserves url() content") {
    val input  = """a { background: url( /images/bg.png ) }"""
    val result = CssMinifier.minify(input)
    assert(result.contains("url( /images/bg.png )"), s"Expected url() content preserved, got: $result")
  }

  test("preserves quoted string content") {
    val input  = """a::before { content: "  hello  " }"""
    val result = CssMinifier.minify(input)
    assert(result.contains("\"  hello  \""), s"Expected string content preserved, got: $result")
  }

  test("handles media queries") {
    val input  = "@media ( max-width : 768px ) { body { font-size : 14px } }"
    val result = CssMinifier.minify(input)
    assertEquals(result, "@media (max-width:768px){body{font-size:14px}}")
  }

  test("preserves @import") {
    val input  = """@import url("styles.css");"""
    val result = CssMinifier.minify(input)
    assert(result.contains("@import"), s"Expected @import preserved, got: $result")
  }

  test("preserves @charset") {
    val input  = """@charset "UTF-8"; body { color: red; }"""
    val result = CssMinifier.minify(input)
    assert(result.contains("@charset"), s"Expected @charset preserved, got: $result")
  }

  test("already minified is idempotent") {
    val input = "a{color:red}b{margin:0}"
    assertEquals(CssMinifier.minify(input), input)
  }

  test("multiple selectors with newlines") {
    val input =
      """h1,
        |h2,
        |h3 {
        |  color: red;
        |  margin: 0;
        |}""".stripMargin
    assertEquals(CssMinifier.minify(input), "h1,h2,h3{color:red;margin:0}")
  }

  test("real-world CSS snippet") {
    val input =
      """/* Reset */
        |* { margin: 0; padding: 0; }
        |
        |body {
        |  font-family: Arial, sans-serif;
        |  color: #333333;
        |  background: #ffffff;
        |}
        |
        |.container {
        |  max-width: 960px;
        |  margin: 0px auto;
        |}""".stripMargin
    val result = CssMinifier.minify(input)
    assertEquals(
      result,
      "*{margin:0;padding:0}body{font-family:Arial,sans-serif;color:#333;background:#fff}.container{max-width:960px;margin:0 auto}"
    )
  }

  test("respects options to disable features") {
    val input = "a { color: #ffffff; margin: 0px; /* comment */ }"
    val opts  = CssMinifyOptions(
      removeComments = false,
      shortenColors = false,
      collapseZeros = false
    )
    val result = CssMinifier.minify(input, opts)
    // Comments preserved, colors not shortened, zeros not collapsed
    assert(result.contains("/* comment */"), s"Expected comment preserved, got: $result")
    assert(result.contains("#ffffff"), s"Expected color not shortened, got: $result")
    assert(result.contains("0px"), s"Expected zero not collapsed, got: $result")
  }
}
