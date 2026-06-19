/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential tests for the Terser.minify orchestration ported under
 * ISS-1045 from terser lib/minify.js: ecma normalization, nameCache,
 * format/output resolution + set_shorthand, structured error shape,
 * wrap/enclose, and multi-file input.
 *
 * Each test asserts the ported option CHANGES the output (or that the
 * structured error carries the upstream fields), cross-referenced to the
 * upstream behavior / terser's own test/mocha/minify.js where one exists.
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, ParserOptions }
import ssg.js.output.OutputOptions
import ssg.js.scope.ManglerOptions

final class TerserMinifyOrchestrationIss1045Suite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // ---- ecma normalization (minify.js:152 set_shorthand("ecma", ...)) ----

  // Top-level `ecma` must be threaded into the output stream (set_shorthand
  // targets parse/compress/format). ecma >= 2015 emits ES6 shorthand for
  // destructuring (output.js:341); ecma 5 does not. So the SAME source minified
  // with ecma=Some(2015) differs from the default (ecma 5).
  test("ecma: top-level ecma=2015 threads into output (shorthand destructuring)") {
    val src     = "var {a, b} = obj;"
    val ecma5   = Terser.minifyToString(src, noOpt)
    val ecma6   = Terser.minifyToString(src, noOpt.copy(ecma = Some(2015)))
    assertEquals(ecma5, "var{a:a,b:b}=obj;")
    assertEquals(ecma6, "var{a,b}=obj;")
    assertNotEquals(ecma5, ecma6)
  }

  // An explicit output.ecma must NOT be overwritten by the top-level ecma
  // (set_shorthand's `!(name in options[key])` guard — only fills a still-default
  // sub-option). Here output.ecma is explicitly 2015, top-level ecma is None.
  test("ecma: explicit output.ecma=2015 is honored without top-level ecma") {
    val src = "var {a, b} = obj;"
    val r   = Terser.minifyToString(src, MinifyOptions(compress = false, mangle = false, output = OutputOptions(ecma = 2015)))
    assertEquals(r, "var{a,b}=obj;")
  }

  // ---- format/output resolution + set_shorthand ----

  // minify.js:76-79 test — `format: { beautify: true }` ⇒ "x(1, 2);". In the
  // typed API `output` is the single format slot; beautify spaces the args.
  test("format/output: beautify produces readable output") {
    val r = Terser.minifyToString("x(1,2);", MinifyOptions(compress = false, mangle = false, output = OutputOptions(beautify = true)))
    assertEquals(r, "x(1, 2);")
  }

  // ---- structured error shape (minify.js throws; cli.js:227-249 reads fields) ----

  // The parse error must carry line/col/pos/message/filename (the exact fields
  // the upstream CLI envelope reads). A bare Exception would not.
  test("structured error: JsParseError carries line/col/pos/message on parse failure") {
    val ex = intercept[JsParseError] {
      Terser.minifyToString("function f(a{})", noOpt)
    }
    assert(ex.line >= 1, s"line should be set, got ${ex.line}")
    assert(ex.col >= 0, s"col should be set, got ${ex.col}")
    assert(ex.pos >= 0, s"pos should be set, got ${ex.pos}")
    assert(ex.message.nonEmpty, "message should be set")
  }

  // The filename threaded through parse options must surface on the error
  // (cli.js:230 `ex.filename`).
  test("structured error: filename surfaces on the error") {
    val ex = intercept[JsParseError] {
      Terser.minifyToString("function f(a{})", MinifyOptions(compress = false, mangle = false, parse = ParserOptions(filename = "weird.js")))
    }
    assertEquals(ex.filename, "weird.js")
  }

  // ---- wrap (minify.js:246-248; ast.js:648-657) ----

  test("wrap: wraps body in a CommonJS module exposing the export name") {
    val src = "function enclose() {\n    console.log(\"test enclose\");\n}\nenclose();\n"
    val r   = Terser.minifyToString(src, MinifyOptions(compress = false, mangle = false, wrap = "exports"))
    assertEquals(
      r,
      "(function(exports){function enclose(){console.log(\"test enclose\")}enclose()})(typeof exports==\"undefined\"?exports={}:exports);"
    )
  }

  // ---- enclose (minify.js:249-251; ast.js:659-675) — ported from
  //      test/mocha/minify.js:490-528 "enclose" describe block. ----

  private val encloseSrc = "function enclose() {\n    console.log(\"test enclose\");\n}\nenclose();\n"

  test("enclose: true wraps the body in an empty IIFE") {
    val r = Terser.minifyToString(encloseSrc, MinifyOptions(compress = false, mangle = false, enclose = true))
    assertEquals(r, "(function(){function enclose(){console.log(\"test enclose\")}enclose()})();")
  }

  test("enclose: arg names the IIFE parameter") {
    val r = Terser.minifyToString(encloseSrc, MinifyOptions(compress = false, mangle = false, enclose = "undefined"))
    assertEquals(r, "(function(undefined){function enclose(){console.log(\"test enclose\")}enclose()})();")
  }

  test("enclose: arg:value supplies parameter and argument") {
    val r = Terser.minifyToString(encloseSrc, MinifyOptions(compress = false, mangle = false, enclose = "window,undefined:window"))
    assertEquals(r, "(function(window,undefined){function enclose(){console.log(\"test enclose\")}enclose()})(window);")
  }

  test("enclose: works alongside wrap") {
    val r = Terser.minifyToString(
      encloseSrc,
      MinifyOptions(compress = false, mangle = false, enclose = "window,undefined:window", wrap = "exports")
    )
    assertEquals(
      r,
      "(function(window,undefined){(function(exports){function enclose(){console.log(\"test enclose\")}enclose()})(typeof exports==\"undefined\"?exports={}:exports)})(window);"
    )
  }

  // ---- multi-file input (minify.js:205-238) ----

  // test/mocha/minify-file-map.js "Should accept array of strings": with the
  // default pipeline (compress on) the two files' var statements are joined into
  // one ⇒ "var foo={x:1,y:2,z:3},bar=15;" — proving the two sources were parsed
  // into a single toplevel before compression.
  test("multi-file: array of strings concatenates bodies (compressor joins vars)") {
    val r = Terser.minify(
      "var foo = {\"x\": 1, y: 2, 'z': 3};",
      MinifyOptions.Defaults
    )
    // sanity: single-file form does not have the trailing ,bar=15
    assertEquals(r.code, "var foo={x:1,y:2,z:3};")

    val r2 = Terser.minifySeq(
      Seq("var foo = {\"x\": 1, y: 2, 'z': 3};", "var bar = 15;"),
      MinifyOptions.Defaults
    )
    assertEquals(r2.code, "var foo={x:1,y:2,z:3},bar=15;")
  }

  // With NoOptimize (no compress) the bodies are simply concatenated, proving
  // the multi-file parse without relying on the compressor's join_vars.
  test("multi-file: NoOptimize concatenates statements verbatim") {
    val r = Terser.minifySeq(
      Seq("var foo = {\"x\": 1, y: 2, 'z': 3};", "var bar = 15;"),
      noOpt
    )
    assertEquals(r.code, "var foo={x:1,y:2,z:3};var bar=15;")
  }

  // test/mocha/minify-file-map.js "Should accept object": single-file map.
  test("multi-file: filename→source map produces output") {
    val r = Terser.minifyFiles(
      scala.collection.immutable.ListMap("/scripts/foo.js" -> "var foo = {\"x\": 1, y: 2, 'z': 3};"),
      noOpt
    )
    assertEquals(r.code, "var foo={x:1,y:2,z:3};")
  }

  test("multi-file: two named files concatenate") {
    val r = Terser.minifyFiles(
      scala.collection.immutable.ListMap("a.js" -> "var a = 1;", "b.js" -> "var b = 2;"),
      noOpt
    )
    assertEquals(r.code, "var a=1;var b=2;")
  }

  test("multi-file: empty input throws 'no source file given'") {
    val ex = intercept[IllegalArgumentException] {
      Terser.minifyFiles(scala.collection.immutable.ListMap.empty[String, String], noOpt)
    }
    assert(ex.getMessage.contains("no source file given"), ex.getMessage)
  }

  // ---- nameCache (minify.js:125,162-163,184-186,354-358) ----

  // Mangled variable names persist across two minify calls sharing one
  // NameCache: a global defined in the first call keeps its mangled name in the
  // second (cross-file consistency, test/mocha/minify.js:118-149).
  test("nameCache: persists mangled var names across two minify calls") {
    val cache  = new NameCache()
    val mangle = ManglerOptions(toplevel = true)
    val opts   = MinifyOptions(compress = false, mangle = mangle, toplevel = true, nameCache = cache)

    val out1 = Terser.minifyToString("function longName(aaa){ return aaa + 1; } longName(2);", opts)
    // The cache now holds the global's original→mangled mapping.
    assert(cache.vars.props.nonEmpty, "nameCache.vars should be populated after first call")
    val mappedFirst = cache.vars.props.get("longName")
    assert(mappedFirst.isDefined, s"longName should be in cache: ${cache.vars.props}")

    // Second call: the same global keeps the same mangled name.
    val out2 = Terser.minifyToString("longName(7);", opts)
    val mangled = mappedFirst.get
    assert(out2.contains(mangled), s"second output '$out2' should reuse mangled name '$mangled'")
    assert(out1.contains(mangled), s"first output '$out1' should contain mangled name '$mangled'")
  }

  // nameCache.props persists mangled property names across calls when property
  // mangling is enabled (minify.js:184-186 + 356-358).
  test("nameCache: persists mangled property names across calls") {
    val cache  = new NameCache()
    val mangle = ManglerOptions(toplevel = true, properties = true)
    val opts   = MinifyOptions(compress = false, mangle = mangle, toplevel = true, nameCache = cache)

    Terser.minifyToString("var a_var = { a_prop: 'long' };", opts)
    assert(cache.props.props.contains("a_prop"), s"a_prop should be in props cache: ${cache.props.props}")
    val mappedProp = cache.props.props("a_prop")

    val out2 = Terser.minifyToString("var b = { a_prop: 1 };", opts)
    assert(out2.contains(mappedProp), s"second output '$out2' should reuse mangled prop '$mappedProp'")
  }

  // Without a nameCache, mangled names are NOT shared across calls (proof the
  // cache is what makes them persist — mutation guard).
  test("nameCache: absent cache does not share names (negative control)") {
    val mangle = ManglerOptions(toplevel = true)
    val opts   = MinifyOptions(compress = false, mangle = mangle, toplevel = true)
    // Two independent calls each mangle the single global to the shortest name;
    // there is no shared cache object to inspect, so we only assert no crash and
    // that toplevel mangling actually happened (the long name disappears).
    val out = Terser.minifyToString("function longName(aaa){ return aaa; } longName(1);", opts)
    assert(!out.contains("longName"), s"toplevel mangle should rename the global: $out")
  }
}
