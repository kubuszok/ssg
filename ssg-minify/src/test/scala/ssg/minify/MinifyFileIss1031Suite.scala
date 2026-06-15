/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

/** Unit tests for `Minifier.minifyFile` covering exclude globs, `.min` passthrough, and
  * per-extension dispatch — ISS-1031.
  *
  * Each test cites the original Ruby line numbers from `original-src/jekyll-minifier/lib/jekyll-minifier.rb`
  * (upstream-commit 5422b3570321668b419ec8271391a029f385c390). Does NOT duplicate ISS-1026/ISS-1027
  * tests in `MinifyFileIss1026Iss1027Suite`; this suite exercises the GENERAL exclude, passthrough, and
  * dispatch behavior.
  */
final class MinifyFileIss1031Suite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // Sample inputs — chosen so each compressor produces a DISTINGUISHABLE output,
  // allowing us to verify dispatch went to the right compressor.
  // ---------------------------------------------------------------------------

  /** CSS input that CssMinifier visibly transforms (whitespace + color shortening). */
  private val cssInput = "body { color : #ffffff ; }\n"

  /** JS input that the basic JsMinifier visibly transforms (comment removal). */
  private val jsInput = "var x = 1; // a comment\n"

  /** HTML input that HtmlMinifier visibly transforms (comment removal). */
  private val htmlInput = "<!-- remove --><p>hello</p>"

  /** JSON input that JsonMinifier visibly transforms (whitespace removal). */
  private val jsonInput = """{ "key" : "value" }"""

  /** XML input — routed to HtmlMinifier (rb:972 output_html for non-js/css/json extensions). */
  private val xmlInput = "<!-- remove --><root>content</root>"

  // ---------------------------------------------------------------------------
  // 1. Exclude globs — general behavior (rb:1091-1093)
  //    exclude?(dest, dest_path): exclude.any? { |e| e == file_name || File.fnmatch(e, file_name) }
  //    File.fnmatch called with NO flags => FNM_PATHNAME OFF => '*' matches '/'.
  // ---------------------------------------------------------------------------

  test("exclude: wildcard glob '*.js' excludes a nested JS file (rb:1093 fnmatch, FNM_PATHNAME off)") {
    // Ruby: File.fnmatch('*.js', 'scripts/app.js') #=> true (FNM_PATHNAME off, '*' spans '/').
    // The file must pass through unchanged.
    val options = MinifyOptions(exclude = List("*.js"))
    val result  = Minifier.minifyFile(jsInput, "scripts/app.js", options)
    assertEquals(result, jsInput, "excluded file must pass through byte-identical")
  }

  test("exclude: wildcard glob '*.html' excludes a deeply nested HTML file (rb:1093)") {
    // Ruby: File.fnmatch('*.html', 'en/blog/post.html') #=> true.
    val options = MinifyOptions(exclude = List("*.html"))
    val result  = Minifier.minifyFile(htmlInput, "en/blog/post.html", options)
    assertEquals(result, htmlInput, "excluded HTML file must pass through unchanged")
  }

  test("exclude: no-wildcard pattern 'css' does NOT exclude 'assets/main.css' (rb:1093)") {
    // Ruby: 'css' == 'assets/main.css' #=> false; File.fnmatch('css', 'assets/main.css') #=> false.
    // The file should be MINIFIED, not passed through.
    val options  = MinifyOptions(exclude = List("css"))
    val result   = Minifier.minifyFile(cssInput, "assets/main.css", options)
    val expected = Minifier.minify(cssInput, FileType.Css, options)
    assertEquals(result, expected, "non-matching no-wildcard pattern must not suppress minification")
    assertNotEquals(result, cssInput, "file must be minified, not passed through")
  }

  test("exclude: exact-name pattern matches via '==' arm (rb:1093 'e == file_name')") {
    // Ruby: 'assets/data.json' == 'assets/data.json' #=> true => excluded.
    val options = MinifyOptions(exclude = List("assets/data.json"))
    val result  = Minifier.minifyFile(jsonInput, "assets/data.json", options)
    assertEquals(result, jsonInput, "exact-name exclude must pass the file through unchanged")
  }

  test("exclude: multiple patterns, only one needs to match (rb:1093 'any?')") {
    // Ruby: exclude.any? — short-circuits on first match.
    // '*.html' does NOT match 'style.css', but '*.css' DOES.
    val options = MinifyOptions(exclude = List("*.html", "*.css"))
    val result  = Minifier.minifyFile(cssInput, "style.css", options)
    assertEquals(result, cssInput, "any matching pattern in the exclude list suffices")
  }

  test("exclude: empty exclude list means nothing is excluded") {
    val options = MinifyOptions(exclude = Nil)
    val result  = Minifier.minifyFile(cssInput, "style.css", options)
    assertNotEquals(result, cssInput, "with no excludes the file must be minified")
  }

  test("exclude: glob 'vendor/*' excludes 'vendor/jquery.js' (rb:1093)") {
    // Ruby: File.fnmatch('vendor/*', 'vendor/jquery.js') #=> true.
    val options = MinifyOptions(exclude = List("vendor/*"))
    val result  = Minifier.minifyFile(jsInput, "vendor/jquery.js", options)
    assertEquals(result, jsInput, "glob vendor/* must match vendor/jquery.js")
  }

  test("exclude: glob 'vendor/*' also excludes 'vendor/sub/deep.js' since FNM_PATHNAME is off (rb:1093)") {
    // Ruby: File.fnmatch('vendor/*', 'vendor/sub/deep.js') #=> true (FNM_PATHNAME off, '*' matches '/').
    val options = MinifyOptions(exclude = List("vendor/*"))
    val result  = Minifier.minifyFile(jsInput, "vendor/sub/deep.js", options)
    assertEquals(result, jsInput, "glob vendor/* must match across / with FNM_PATHNAME off")
  }

  // ---------------------------------------------------------------------------
  // 2. `.min` passthrough (rb:976-990 output_js_or_file / output_css_or_file)
  //    path.end_with?('.min.js') => output_file (untouched)
  //    path.end_with?('.min.css') => output_file (untouched)
  //    NO .min guard for .json (rb:967-968 dispatches .json straight to output_json).
  // ---------------------------------------------------------------------------

  test(".min passthrough: nested/path/lib.min.js passes through (rb:977 end_with?)") {
    // A nested .min.js path still satisfies end_with? and must not be re-minified.
    val input  = "var a=1;/* license */\n"
    val result = Minifier.minifyFile(input, "nested/path/lib.min.js")
    assertEquals(result, input, ".min.js file must pass through byte-identical regardless of nesting")
  }

  test(".min passthrough: theme.min.css passes through (rb:985 end_with?)") {
    val input  = "body { margin : 0 }\n"
    val result = Minifier.minifyFile(input, "theme.min.css")
    assertEquals(result, input, ".min.css file must pass through byte-identical")
  }

  test(".min passthrough: data.min.json is still minified (rb:967-968, no .min guard for json)") {
    // Ruby dispatches '.json' straight to output_json (rb:967-968), with no .min check.
    // Verify that data.min.json is actually minified (NOT passed through).
    val input  = """{ "a" : 1 }"""
    val result = Minifier.minifyFile(input, "data.min.json")
    assertEquals(result, """{"a":1}""", ".min.json has no passthrough in Ruby — must still be minified")
  }

  test(".min passthrough: regular .js file is minified, not passed through") {
    val result = Minifier.minifyFile(jsInput, "assets/app.js")
    assert(!result.contains("// a comment"), s"regular .js must be minified, got: $result")
  }

  test(".min passthrough: regular .css file is minified, not passed through") {
    val result = Minifier.minifyFile(cssInput, "assets/style.css")
    assertNotEquals(result, cssInput, "regular .css must be minified")
  }

  // ---------------------------------------------------------------------------
  // 3. Per-extension dispatch — each extension routes to the correct compressor.
  //    Ruby: rb:962-974 output_compressed for Document/Page:
  //      '.js'   => output_js_or_file (=> output_js => Terser)
  //      '.json' => output_json       (=> JSON.minify)
  //      '.css'  => output_css_or_file (=> output_css => CSSminify2)
  //      else    => output_html       (=> HtmlCompressor) — covers .html, .xml
  //    Ruby: rb:1156-1171 process_static_file for StaticFile:
  //      '.js'   => process_js_file
  //      '.json' => output_json
  //      '.css'  => process_css_file
  //      '.xml'  => output_html
  //      else    => copy_file (passthrough)
  //    The port's fileTypeFromPath + minify dispatch mirrors this.
  // ---------------------------------------------------------------------------

  test("dispatch: .html routes to HtmlMinifier — comments removed (rb:972 output_html)") {
    val result = Minifier.minifyFile(htmlInput, "page.html")
    assert(!result.contains("<!-- remove -->"), s"HTML comment must be removed, got: $result")
    assert(result.contains("<p>hello</p>"), s"HTML content must be preserved, got: $result")
  }

  test("dispatch: .htm routes to HtmlMinifier — same as .html") {
    val result = Minifier.minifyFile(htmlInput, "page.htm")
    assert(!result.contains("<!-- remove -->"), s"HTM comment must be removed, got: $result")
    assert(result.contains("<p>hello</p>"), s"HTM content must be preserved, got: $result")
  }

  test("dispatch: .xml routes to HtmlMinifier — comments removed (rb:972 output_html / rb:1168 StaticFile)") {
    val result = Minifier.minifyFile(xmlInput, "feed.xml")
    assert(!result.contains("<!-- remove -->"), s"XML comment must be removed, got: $result")
    assert(result.contains("<root>content</root>"), s"XML content must be preserved, got: $result")
  }

  test("dispatch: .css routes to CssMinifier — whitespace collapsed (rb:969 output_css)") {
    val result = Minifier.minifyFile(cssInput, "style.css")
    // CssMinifier collapses whitespace and shortens colors: "body{color:#fff}"
    assertEquals(result, "body{color:#fff}", "CSS must be minified by CssMinifier")
  }

  test("dispatch: .js routes to JsMinifier — comments removed (rb:965 output_js)") {
    val result = Minifier.minifyFile(jsInput, "app.js")
    assert(!result.contains("// a comment"), s"JS comment must be removed, got: $result")
    assert(result.contains("var x"), s"JS code must be preserved, got: $result")
  }

  test("dispatch: .json routes to JsonMinifier — whitespace removed (rb:967 output_json)") {
    val result = Minifier.minifyFile(jsonInput, "data.json")
    assertEquals(result, """{"key":"value"}""", "JSON must be minified by JsonMinifier")
  }

  test("dispatch: unknown extension passes through unchanged (rb:1170 StaticFile copy_file)") {
    // Ruby StaticFile: unknown extensions fall to `copy_file(path, dest_path)` — no transformation.
    // The port's fileTypeFromPath returns None, and minifyFile returns input unchanged.
    val input  = "binary or unknown content"
    val result = Minifier.minifyFile(input, "image.png")
    assertEquals(result, input, "unknown extension must pass through unchanged")
  }

  test("dispatch: no extension passes through unchanged") {
    val input  = "no extension content"
    val result = Minifier.minifyFile(input, "Makefile")
    assertEquals(result, input, "file with no recognized extension must pass through unchanged")
  }

  // ---------------------------------------------------------------------------
  // 4. Interaction: exclude takes priority over dispatch and .min passthrough.
  //    Ruby: exclude? is checked FIRST (rb:1109-1113 Document, rb:1146-1150 StaticFile),
  //    before output_compressed/process_static_file which contain the .min and dispatch logic.
  // ---------------------------------------------------------------------------

  test("exclude overrides dispatch: excluded .css file is NOT minified (rb:1109 exclude? checked first)") {
    val options = MinifyOptions(exclude = List("vendor/*.css"))
    val result  = Minifier.minifyFile(cssInput, "vendor/reset.css", options)
    assertEquals(result, cssInput, "exclude must take priority over CSS minification dispatch")
  }

  test("exclude overrides .min passthrough: excluded .min.js is still passed through (both paths return input)") {
    // When both exclude and .min.js apply, the result is the same: input returned unchanged.
    // But the exclude check runs FIRST in the port's code (line 84), so .min.js logic is not reached.
    val options = MinifyOptions(exclude = List("*.js"))
    val input   = "var a=1;/* lic */\n"
    val result  = Minifier.minifyFile(input, "lib.min.js", options)
    assertEquals(result, input, "excluded .min.js file must still pass through unchanged")
  }

  // ---------------------------------------------------------------------------
  // 5. Toggle interaction: compressCss/compressJs/compressJson toggles off => passthrough
  //    Ruby: output_js checks config.compress_javascript? (rb:1029),
  //           output_css checks config.compress_css? (rb:1061),
  //           output_json checks config.compress_json? (rb:1045).
  //    When off, the content is written unchanged (output_file).
  // ---------------------------------------------------------------------------

  test("toggle: compressCss=false passes CSS through even for .css file (rb:1061)") {
    val options = MinifyOptions(compressCss = false)
    val result  = Minifier.minifyFile(cssInput, "style.css", options)
    assertEquals(result, cssInput, "CSS toggle off must pass through unchanged")
  }

  test("toggle: compressJs=false passes JS through even for .js file (rb:1029)") {
    val options = MinifyOptions(compressJs = false)
    val result  = Minifier.minifyFile(jsInput, "app.js", options)
    assertEquals(result, jsInput, "JS toggle off must pass through unchanged")
  }

  test("toggle: compressJson=false passes JSON through even for .json file (rb:1045)") {
    val options = MinifyOptions(compressJson = false)
    val result  = Minifier.minifyFile(jsonInput, "data.json", options)
    assertEquals(result, jsonInput, "JSON toggle off must pass through unchanged")
  }

  // ---------------------------------------------------------------------------
  // 6. Custom JsCompressor via minifyFile
  //    Ruby: Terser is the JS compressor (rb:1 require 'terser'). The port allows
  //    plugging a custom JsCompressor. Verify the custom compressor is called for .js files.
  // ---------------------------------------------------------------------------

  test("custom JsCompressor is used for .js dispatch via minifyFile") {
    var called = false
    val spy = new JsCompressor {
      override def compress(input: String): String = {
        called = true
        "CUSTOM:" + input
      }
    }
    val result = Minifier.minifyFile(jsInput, "app.js", jsCompressor = spy)
    assert(called, "custom JsCompressor must be invoked for .js files")
    assert(result.startsWith("CUSTOM:"), s"expected custom compressor output, got: $result")
  }

  test("custom JsCompressor is used for .html dispatch (inline JS) via minifyFile") {
    var called = false
    val spy = new JsCompressor {
      override def compress(input: String): String = {
        called = true
        input // passthrough
      }
    }
    // HTML with a <script> tag — the HtmlMinifier will invoke the jsCompressor on inline JS.
    val input  = "<script>var x = 1; // inline</script>"
    val _      = Minifier.minifyFile(input, "page.html", jsCompressor = spy)
    assert(called, "custom JsCompressor must be invoked for inline JS in HTML files")
  }
}
