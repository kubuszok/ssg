/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

final class MinifierSuite extends munit.FunSuite {

  test("minifyHtml removes comments") {
    val result = Minifier.minifyHtml("<!-- comment --><p>hello</p>")
    assert(!result.contains("<!-- comment -->"), s"got: $result")
    assert(result.contains("<p>hello</p>"), s"got: $result")
  }

  test("minifyCss collapses whitespace") {
    val result = Minifier.minifyCss("body { color : red ; }")
    assertEquals(result, "body{color:red}")
  }

  test("minifyJs removes comments") {
    val result = Minifier.minifyJs("var x = 1; // comment")
    assert(!result.contains("// comment"), s"got: $result")
  }

  test("minifyJson removes whitespace") {
    val result = Minifier.minifyJson("""{ "a" : 1 }""")
    assertEquals(result, """{"a":1}""")
  }

  test("minify dispatches by FileType.Html") {
    val result = Minifier.minify("<!-- x --><p>hi</p>", FileType.Html)
    assert(!result.contains("<!-- x -->"), s"got: $result")
  }

  test("minify dispatches by FileType.Css") {
    val result = Minifier.minify("a { color : red ; }", FileType.Css)
    assertEquals(result, "a{color:red}")
  }

  test("minify dispatches by FileType.Js") {
    val result = Minifier.minify("var x = 1; // c", FileType.Js)
    assert(!result.contains("// c"), s"got: $result")
  }

  test("minify dispatches by FileType.Json") {
    val result = Minifier.minify("{ }", FileType.Json)
    assertEquals(result, "{}")
  }

  test("minify dispatches by FileType.Xml") {
    val result = Minifier.minify("<!-- comment --><root></root>", FileType.Xml)
    assert(!result.contains("<!-- comment -->"), s"got: $result")
  }

  test("fileTypeFromPath detects html") {
    assertEquals(Minifier.fileTypeFromPath("index.html"), Some(FileType.Html))
    assertEquals(Minifier.fileTypeFromPath("page.htm"), Some(FileType.Html))
  }

  test("fileTypeFromPath detects css") {
    assertEquals(Minifier.fileTypeFromPath("style.css"), Some(FileType.Css))
  }

  test("fileTypeFromPath detects js") {
    assertEquals(Minifier.fileTypeFromPath("app.js"), Some(FileType.Js))
  }

  test("fileTypeFromPath detects json") {
    assertEquals(Minifier.fileTypeFromPath("data.json"), Some(FileType.Json))
  }

  test("fileTypeFromPath detects xml") {
    assertEquals(Minifier.fileTypeFromPath("feed.xml"), Some(FileType.Xml))
  }

  test("fileTypeFromPath returns None for unknown") {
    assertEquals(Minifier.fileTypeFromPath("image.png"), None)
    assertEquals(Minifier.fileTypeFromPath("noext"), None)
  }

  test("custom JsCompressor via minify") {
    val passthrough = new JsCompressor {
      override def compress(input: String): String = input
    }
    val input  = "<script>var x = 1; // kept</script>"
    val result = Minifier.minify(input, FileType.Html, jsCompressor = passthrough)
    assert(result.contains("// kept"), s"Expected passthrough JS compressor, got: $result")
  }

  test("end-to-end: full page minification") {
    val input =
      """<!DOCTYPE html>
        |<html>
        |<head>
        |  <title>Test</title>
        |  <style>  body { color: #ffffff; }  </style>
        |</head>
        |<body>
        |  <p>Hello    World</p>
        |  <script>  var x = 1; // init  </script>
        |</body>
        |</html>""".stripMargin

    val result = Minifier.minifyHtml(input)
    // Multi-spaces collapsed
    assert(result.contains("Hello World"), s"Expected spaces collapsed")
    // CSS minified
    assert(result.contains("color:#fff"), s"Expected CSS minified in: $result")
    // JS comment removed
    assert(!result.contains("// init"), s"Expected JS comment removed")
  }

  test("XML minification removes comments and collapses whitespace") {
    val input  = "<root>  <child>  text  </child>  </root>"
    val result = Minifier.minify(input, FileType.Xml)
    assert(result.contains("<root>"), s"Expected root element, got: $result")
    assert(result.contains("<child>"), s"Expected child element, got: $result")
    assert(result.contains("text"), s"Expected text content, got: $result")
  }

  test("fileTypeFromPath returns None for unknown extension and minify does not crash") {
    assertEquals(Minifier.fileTypeFromPath("data.bin"), None)
    assertEquals(Minifier.fileTypeFromPath("archive.tar.gz"), None)
    assertEquals(Minifier.fileTypeFromPath(""), None)
  }
}
