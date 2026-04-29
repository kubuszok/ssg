/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

import ssg.minify.html.{ HtmlMinifier, HtmlMinifyOptions }

final class HtmlMinifySuite extends munit.FunSuite {

  test("empty string") {
    assertEquals(HtmlMinifier.minify(""), "")
  }

  // -- Comment removal --

  test("removes HTML comments") {
    val input  = "<p>hello</p><!-- comment --><p>world</p>"
    val result = HtmlMinifier.minify(input)
    assert(!result.contains("<!-- comment -->"), s"Expected comment removed, got: $result")
    assert(result.contains("<p>hello</p>"), s"Expected content preserved, got: $result")
    assert(result.contains("<p>world</p>"), s"Expected content preserved, got: $result")
  }

  test("preserves conditional comments") {
    val input  = "<!--[if IE 8]><link href=\"ie8.css\"><![endif]-->"
    val result = HtmlMinifier.minify(input)
    assert(result.contains("<!--[if IE 8]>"), s"Expected conditional comment preserved, got: $result")
  }

  test("removes multiple comments") {
    val input  = "<!-- a --><p>x</p><!-- b -->"
    val result = HtmlMinifier.minify(input)
    assert(!result.contains("<!-- a -->"), s"got: $result")
    assert(!result.contains("<!-- b -->"), s"got: $result")
  }

  // -- Whitespace --

  test("collapses multiple spaces") {
    val input  = "<p>hello    world</p>"
    val result = HtmlMinifier.minify(input)
    assertEquals(result, "<p>hello world</p>")
  }

  test("removes inter-tag spaces when enabled") {
    val input  = "<p>hello</p>  \n  <p>world</p>"
    val opts   = HtmlMinifyOptions(removeIntertagSpaces = true)
    val result = HtmlMinifier.minify(input, opts)
    assertEquals(result, "<p>hello</p><p>world</p>")
  }

  test("removes spaces inside tags") {
    val input  = """<p  class="foo"  id="bar" >text</p>"""
    val result = HtmlMinifier.minify(input)
    assertEquals(result, """<p class="foo" id="bar">text</p>""")
  }

  // -- Doctype --

  test("simplifies doctype when enabled") {
    val input  = """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"><html></html>"""
    val opts   = HtmlMinifyOptions(simpleDoctype = true)
    val result = HtmlMinifier.minify(input, opts)
    assert(result.startsWith("<!DOCTYPE html>"), s"Expected simplified doctype, got: $result")
  }

  // -- Attribute optimization --

  test("removes unnecessary quotes when enabled") {
    val input  = """<div class="container" id="main">text</div>"""
    val opts   = HtmlMinifyOptions(removeQuotes = true)
    val result = HtmlMinifier.minify(input, opts)
    assertEquals(result, "<div class=container id=main>text</div>")
  }

  test("keeps quotes on values with special characters") {
    val input  = """<div class="foo bar">text</div>"""
    val opts   = HtmlMinifyOptions(removeQuotes = true)
    val result = HtmlMinifier.minify(input, opts)
    // "foo bar" has a space, quotes should remain
    assert(result.contains("\"foo bar\""), s"Expected quotes preserved for space value, got: $result")
  }

  test("simplifies boolean attributes when enabled") {
    val input  = """<input checked="checked" disabled="disabled">"""
    val opts   = HtmlMinifyOptions(simpleBooleanAttributes = true)
    val result = HtmlMinifier.minify(input, opts)
    assert(result.contains("checked") && !result.contains("checked=\"checked\""), s"Expected simplified boolean, got: $result")
  }

  // -- Default attribute removal --

  test("removes default script type when enabled") {
    val input  = """<script type="text/javascript" src="app.js"></script>"""
    val opts   = HtmlMinifyOptions(removeScriptAttributes = true, compressJsInHtml = false)
    val result = HtmlMinifier.minify(input, opts)
    assert(!result.contains("type=\"text/javascript\""), s"Expected type removed, got: $result")
    assert(result.contains("<script"), s"Expected script tag preserved, got: $result")
  }

  test("removes default style type when enabled") {
    val input  = """<style type="text/css">body{}</style>"""
    val opts   = HtmlMinifyOptions(removeStyleAttributes = true, compressCssInHtml = false)
    val result = HtmlMinifier.minify(input, opts)
    assert(!result.contains("type=\"text/css\""), s"Expected type removed, got: $result")
  }

  test("removes default form method when enabled") {
    val input  = """<form method="get" action="/search"></form>"""
    val opts   = HtmlMinifyOptions(removeFormAttributes = true)
    val result = HtmlMinifier.minify(input, opts)
    assert(!result.contains("method=\"get\""), s"Expected method removed, got: $result")
  }

  test("removes default input type when enabled") {
    val input  = """<input type="text" name="q">"""
    val opts   = HtmlMinifyOptions(removeInputAttributes = true)
    val result = HtmlMinifier.minify(input, opts)
    assert(!result.contains("type=\"text\""), s"Expected type removed, got: $result")
  }

  // -- Protocol removal --

  test("removes http protocol when enabled") {
    val input  = """<a href="http://example.com">link</a>"""
    val opts   = HtmlMinifyOptions(removeHttpProtocol = true)
    val result = HtmlMinifier.minify(input, opts)
    assert(result.contains("href=\"//example.com\""), s"Expected http:// removed, got: $result")
  }

  test("removes https protocol when enabled") {
    val input  = """<img src="https://example.com/img.png">"""
    val opts   = HtmlMinifyOptions(removeHttpsProtocol = true)
    val result = HtmlMinifier.minify(input, opts)
    assert(result.contains("src=\"//example.com/img.png\""), s"Expected https:// removed, got: $result")
  }

  // -- Preserved blocks --

  test("preserves pre tag content") {
    val input  = "<pre>  code   with   spaces  </pre>"
    val result = HtmlMinifier.minify(input)
    assert(result.contains("<pre>  code   with   spaces  </pre>"), s"Expected pre content preserved, got: $result")
  }

  test("preserves textarea content") {
    val input  = "<textarea>  user   text  </textarea>"
    val result = HtmlMinifier.minify(input)
    assert(result.contains("<textarea>  user   text  </textarea>"), s"Expected textarea content preserved, got: $result")
  }

  test("preserves script tag content") {
    val input  = "<script>  var x  =  1;  </script>"
    val opts   = HtmlMinifyOptions(compressJsInHtml = false)
    val result = HtmlMinifier.minify(input, opts)
    assert(result.contains("var x  =  1"), s"Expected script content preserved, got: $result")
  }

  test("preserves style tag content") {
    val input  = "<style>  body { color: red; }  </style>"
    val opts   = HtmlMinifyOptions(compressCssInHtml = false)
    val result = HtmlMinifier.minify(input, opts)
    assert(result.contains("body { color: red; }"), s"Expected style content preserved, got: $result")
  }

  // -- Inline compression --

  test("compresses inline CSS in style tags") {
    val input  = "<style>  body  { color : red ;  margin : 0 ; }  </style>"
    val result = HtmlMinifier.minify(input)
    assert(result.contains("body{color:red;margin:0}"), s"Expected CSS minified, got: $result")
  }

  test("compresses inline JS in script tags") {
    val input  = "<script>  var x = 1; // comment  </script>"
    val result = HtmlMinifier.minify(input)
    assert(!result.contains("// comment"), s"Expected JS comment removed, got: $result")
    assert(result.contains("var x"), s"Expected JS code preserved, got: $result")
  }

  test("does not compress script with src attribute") {
    val input  = """<script src="app.js">fallback</script>"""
    val result = HtmlMinifier.minify(input)
    assert(result.contains("src=\"app.js\""), s"Expected src preserved, got: $result")
  }

  // -- Preserve patterns --

  test("preserves user-supplied regex patterns") {
    val input  = "<p>Keep <?php echo 'hi'; ?> this</p>"
    val opts   = HtmlMinifyOptions(preservePatterns = List("<\\?php.*?\\?>".r))
    val result = HtmlMinifier.minify(input, opts)
    assert(result.contains("<?php echo 'hi'; ?>"), s"Expected PHP preserved, got: $result")
  }

  // -- Graceful degradation --

  test("returns original on malformed HTML") {
    val input  = "<p>unclosed"
    val result = HtmlMinifier.minify(input)
    assert(result.contains("<p>unclosed"), s"Expected graceful handling, got: $result")
  }

  // -- Real-world HTML --

  test("real-world HTML page") {
    val input =
      """<!DOCTYPE html>
        |<html>
        |<head>
        |  <title>Test</title>
        |  <!-- Meta tags -->
        |  <style>
        |    body { color: #333333; margin: 0px; }
        |  </style>
        |</head>
        |<body>
        |  <div  class="container">
        |    <h1>Hello    World</h1>
        |    <pre>  preserved   spaces  </pre>
        |    <script>
        |      // Initialize
        |      var x = 1;
        |    </script>
        |  </div>
        |</body>
        |</html>""".stripMargin

    val result = HtmlMinifier.minify(input)

    // Comments removed
    assert(!result.contains("<!-- Meta tags -->"), s"Expected comment removed")
    // Multi-spaces collapsed
    assert(!result.contains("Hello    World"), s"Expected spaces collapsed")
    assert(result.contains("Hello World"), s"Expected single space")
    // Pre content preserved
    assert(result.contains("  preserved   spaces  "), s"Expected pre content preserved")
    // Inline CSS minified
    assert(result.contains("color:#333"), s"Expected CSS minified in result: $result")
    // Inline JS comment removed
    assert(!result.contains("// Initialize"), s"Expected JS comment removed")
  }

  test("multiple style and script blocks") {
    val input  = "<style>a { color: red; }</style><p>text</p><style>b { color: blue; }</style>"
    val result = HtmlMinifier.minify(input)
    assert(result.contains("a{color:red}"), s"Expected first CSS minified, got: $result")
    assert(result.contains("b{color:blue}"), s"Expected second CSS minified, got: $result")
  }

  test("handles all options disabled") {
    val input = "<!-- comment -->  <p  class=\"x\" >  hello  </p>"
    val opts  = HtmlMinifyOptions(
      removeComments = false,
      removeMultiSpaces = false,
      removeSpacesInsideTags = false,
      compressCssInHtml = false,
      compressJsInHtml = false
    )
    val result = HtmlMinifier.minify(input, opts)
    assertEquals(result, input)
  }
}
