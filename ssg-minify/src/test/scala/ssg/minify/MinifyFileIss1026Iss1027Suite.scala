/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

/** Red tests for ISS-1026 and ISS-1027 (R0610 campaign), reproducing two `Minifier.minifyFile` bugs.
  *
  * ISS-1026 — exclude matching must use Ruby fnmatch semantics, not substring `contains`.
  *
  * Original: original-src/jekyll-minifier/lib/jekyll-minifier.rb:1091-1093
  * {{{
  *   def exclude?(dest, dest_path)
  *     file_name = dest_path.slice(dest.length+1..dest_path.length)
  *     exclude.any? { |e| e == file_name || File.fnmatch(e, file_name) }
  *   end
  * }}}
  * Semantics pinned by these tests:
  *   - fnmatch target is `file_name` = the destination-RELATIVE path (dest_path with the `dest` prefix and its trailing separator sliced off), NOT the basename and NOT an absolute path.
  *     `Minifier.minifyFile(input, filePath, ...)` receives that relative path.
  *   - `File.fnmatch` is called with NO flags, so File::FNM_PATHNAME is OFF and `*` also matches `/` (Ruby stdlib doc example: `File.fnmatch('*', 'dave/.profile') #=> true`). Hence pattern "*.css"
  *     matches "assets/style.css" across the directory separator.
  *   - A pattern with no wildcards matches only via the `e == file_name` arm or an exact fnmatch: `File.fnmatch('js', 'assets/main.js') #=> false`, so exclude "js" must NOT exclude "assets/main.js".
  *
  * ISS-1027 — pre-minified `.min.js` / `.min.css` files must pass through untouched.
  *
  * Original: original-src/jekyll-minifier/lib/jekyll-minifier.rb:976-990 (Document/Page via output_compressed) and :1174-1188 (StaticFile):
  * {{{
  *   def output_js_or_file(path, context)
  *     if path.end_with?('.min.js')   # rb:977 (and rb:1175 in process_js_file)
  *       output_file(path, context)   # write content untouched
  *     else
  *       output_js(path, context)
  *   def output_css_or_file(path, context)
  *     if path.end_with?('.min.css')  # rb:985 (and rb:1183 in process_css_file)
  *       output_file(path, context)   # write content untouched
  *     else
  *       output_css(path, context)
  * }}}
  * Semantics pinned by these tests:
  *   - the check is `end_with?` on the destination path — a suffix check, NOT `contains`: "assets/minify.js" does not end with ".min.js" and must still be minified.
  *   - passthrough means the content is written byte-identical (output_file/copy_file).
  */
final class MinifyFileIss1026Iss1027Suite extends munit.FunSuite {

  private val cssInput = "body { color : red ; }\n/* banner */\n"
  private val jsInput  = "var x = 1; // keep me\nvar y  =  2;\n"

  // ---------------------------------------------------------------------------
  // ISS-1026 — fnmatch exclude semantics
  // ---------------------------------------------------------------------------

  test("ISS-1026 red: exclude '*.css' excludes assets/style.css (fnmatch, rb:1093)") {
    // Ruby: File.fnmatch('*.css', 'assets/style.css') #=> true (no FNM_PATHNAME, '*' spans '/').
    // Port today: "assets/style.css".contains("*.css") is false -> file gets minified -> red.
    val options = MinifyOptions(exclude = List("*.css"))
    val result  = Minifier.minifyFile(cssInput, "assets/style.css", options)
    assertEquals(result, cssInput, "excluded file must pass through unchanged (rb:1109-1110, rb:1146-1147)")
  }

  test("ISS-1026 red: exclude 'js' does NOT exclude assets/main.js (rb:1093)") {
    // Ruby: 'js' == 'assets/main.js' #=> false; File.fnmatch('js', 'assets/main.js') #=> false
    // -> NOT excluded -> minified. Port today: "assets/main.js".contains("js") is true ->
    // passthrough -> red.
    val options  = MinifyOptions(exclude = List("js"))
    val result   = Minifier.minifyFile(jsInput, "assets/main.js", options)
    val minified = Minifier.minify(jsInput, FileType.Js, options)
    assertEquals(result, minified, "non-matching exclude pattern must not suppress minification")
    assert(!result.contains("// keep me"), s"expected minified output, got passthrough: $result")
  }

  test("ISS-1026 control: exact-name exclude 'vendor/lib.css' excludes vendor/lib.css (rb:1093 '==' arm)") {
    // Ruby: 'vendor/lib.css' == 'vendor/lib.css' #=> true -> excluded. The port's contains()
    // also matches here, so this is a pinning control (green today, must stay green).
    val options = MinifyOptions(exclude = List("vendor/lib.css"))
    val result  = Minifier.minifyFile(cssInput, "vendor/lib.css", options)
    assertEquals(result, cssInput, "exact-name exclude must pass the file through unchanged")
  }

  test("ISS-1026 control: no excludes -> file is minified") {
    val result = Minifier.minifyFile(cssInput, "assets/style.css")
    assertEquals(result, Minifier.minify(cssInput, FileType.Css))
    assertNotEquals(result, cssInput, "without excludes the css file must be minified")
  }

  // ---------------------------------------------------------------------------
  // ISS-1027 — .min.js / .min.css passthrough
  // ---------------------------------------------------------------------------

  test("ISS-1027 red: assets/app.min.js passes through byte-identical (rb:977, rb:1175)") {
    // Content the basic JS minifier visibly changes (comment + double space), so re-minification
    // is detectable. Ruby: path.end_with?('.min.js') -> output_file/copy_file untouched.
    val input  = "var a = 1; // license header\nvar b  =  2;\n"
    val result = Minifier.minifyFile(input, "assets/app.min.js")
    assertEquals(result, input, "pre-minified .min.js must not be re-minified")
  }

  test("ISS-1027 red: assets/styles.min.css passes through byte-identical (rb:985, rb:1183)") {
    val input  = "body { color : #ffffff ; }\n/* banner */\n"
    val result = Minifier.minifyFile(input, "assets/styles.min.css")
    assertEquals(result, input, "pre-minified .min.css must not be re-minified")
  }

  test("ISS-1027 control: assets/app.js (not .min.js) gets minified") {
    val result = Minifier.minifyFile(jsInput, "assets/app.js")
    assertEquals(result, Minifier.minify(jsInput, FileType.Js))
    assert(!result.contains("// keep me"), s"expected minified output, got: $result")
  }

  test("ISS-1027 control: assets/minify.js does not match the '.min.js' suffix check (rb:977 end_with?)") {
    // Guards against an overbroad contains-based passthrough: 'assets/minify.js' contains
    // "min" but does NOT end with '.min.js', so it must be minified.
    val result = Minifier.minifyFile(jsInput, "assets/minify.js")
    assertEquals(result, Minifier.minify(jsInput, FileType.Js))
    assert(!result.contains("// keep me"), s"expected minified output, got: $result")
  }

  test("ISS-1027 control: data.min.json is still minified — Ruby has no .min check for json (rb:967-968, rb:1163-1164)") {
    // output_compressed dispatches '.json' straight to output_json (rb:967-968), and StaticFile
    // likewise (rb:1163-1164); only '.min.js' (rb:977/1175) and '.min.css' (rb:985/1183) pass
    // through. Guards against a fix that passes through every '.min.*' extension.
    val input  = """{ "a" : 1 }"""
    val result = Minifier.minifyFile(input, "data.min.json")
    assertEquals(result, """{"a":1}""", "json passthrough is not part of the Ruby .min logic")
  }
}
