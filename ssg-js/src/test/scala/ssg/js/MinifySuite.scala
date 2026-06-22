/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/minify.js
 * Original: 38 it() calls (1 disabled via `if(0)` in original — "rename").
 *
 * Compression tests run with default MinifyOptions (compress + mangle enabled).
 * Tests requiring unported features (nameCache, enclose, wrap, multi-file, mangle.properties,
 * source map file I/O) are marked .fail with explanatory comments.
 */
package ssg
package js

import ssg.js.compress.CompressorOptions
import ssg.js.parse.JsParseError
import ssg.js.output.OutputOptions
import ssg.js.scope.{ ManglerCache, ManglerOptions, PropManglerOptions }

final class MinifySuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(10, "s")

  private val noOpt = MinifyOptions.NoOptimize

  // terser/test/input/issue-520/input.js — has a decoy `//# sourceMappingURL`
  // comment before the real inline data-URI map (exercises read_source_map's
  // end-anchored regex, minify.js:34).
  private val Issue520Input: String =
    "var Foo = function Foo(){console.log(1+2);}; new Foo();\n\n//# sourceMappingURL=data:application/json;charset=utf-8;base64,I/am/not/a/sourceMappingURL/but/a/comment\n//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjpudWxsLCJzb3VyY2VzIjpbInN0ZGluIl0sInNvdXJjZXNDb250ZW50IjpbImNsYXNzIEZvbyB7IGNvbnN0cnVjdG9yKCl7Y29uc29sZS5sb2coMSsyKTt9IH0gbmV3IEZvbygpO1xuIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiJBQUFBLElBQU0sR0FBRyxHQUFDLEFBQUUsWUFBVyxFQUFFLENBQUMsT0FBTyxDQUFDLEdBQUcsQ0FBQyxDQUFDLENBQUMsQ0FBQyxDQUFDLENBQUMsQ0FBQyxDQUFBLEFBQUUsQ0FBQyxJQUFJLEdBQUcsRUFBRSxDQUFDOyJ9\n"

  // terser/test/input/issue-520/output.js — the expected (code + "\n").
  private val Issue520Output: String =
    "new function(){console.log(3)};\n//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJuYW1lcyI6WyJjb25zb2xlIiwibG9nIl0sInNvdXJjZXMiOlsic3RkaW4iXSwic291cmNlc0NvbnRlbnQiOlsiY2xhc3MgRm9vIHsgY29uc3RydWN0b3IoKXtjb25zb2xlLmxvZygxKzIpO30gfSBuZXcgRm9vKCk7XG4iXSwibWFwcGluZ3MiOiJBQUErQyxJQUFyQyxXQUFnQkEsUUFBUUMsSUFBSSxFQUFLIn0=\n"

  // === Tests from mocha/minify.js ===

  // 1. "Should test basic sanity of minify with default options"
  // Original: minify("function foo(bar) { if (bar) return 3; else return 7; var u = not_called(); }")
  // Expected: "function foo(n){return n?3:7}"
  // Requires compression + mangle
  test("basic sanity of minify with default options") {
    assertEquals(
      Terser.minifyToString("function foo(bar) { if (bar) return 3; else return 7; var u = not_called(); }"),
      "function foo(n){return n?3:7}"
    )
  }

  // 2. "Should have a sync version" — ssg-js is always sync, test the API
  // Original: minify_sync("console.log(1 + 1);") → "console.log(2);"
  // Requires compression for constant folding
  test("sync version (parse+output only, compression needed for constant folding)") {
    assertEquals(Terser.minifyToString("console.log(1 + 1);"), "console.log(2);")
  }

  // 3. "Should skip inherited keys from files" — JS-specific multi-file API, not applicable

  // 4. "Should not mutate options" — Scala uses immutable case classes, always true by design

  // 5-6. nameCache tests — ported (ISS-1045); see the nameCache tests below.

  // 7. "Should accept new format options as well as output options"
  // Original: minify("x(1,2);", { format: { beautify: true }}) → "x(1, 2);"
  test("beautify format option produces readable output") {
    val result = Terser.minifyToString(
      "x(1,2);",
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(beautify = true))
    )
    assertEquals(result, "x(1, 2);")
  }

  // 8. "Should refuse format and output option together" — not applicable (Scala has one output field)

  // 9-12. mangle.cache, nameCache, property mangling cache tests — ported (ISS-1045).

  // 13. "Should not parse invalid use of reserved words"
  test("should not parse invalid use of reserved words — enum is allowed as identifier") {
    // In non-strict mode, enum is allowed as an identifier
    val result = Terser.minifyToString("function enum(){}", noOpt)
    assert(result.nonEmpty, "enum as function name should parse")
  }

  test("should not parse invalid use of reserved words — static is allowed as identifier") {
    val result = Terser.minifyToString("function static(){}", noOpt)
    assert(result.nonEmpty, "static as function name should parse")
  }

  // Note: In upstream Terser, "function super(){}" and "function this(){}" throw parse errors.
  // The ssg-js parser currently allows these — this is a known gap.
  // When the parser is fixed, these should use intercept[JsParseError].
  test("should not parse invalid use of reserved words — super is rejected".fail) {
    intercept[JsParseError] {
      Terser.minifyToString("function super(){}", noOpt)
    }
  }

  test("should not parse invalid use of reserved words — this is rejected".fail) {
    intercept[JsParseError] {
      Terser.minifyToString("function this(){}", noOpt)
    }
  }

  // 14-16. keep_quoted_props tests
  test("keep_quoted_props: preserve quotes in object literals") {
    val js     = "var foo = {\"x\": 1, y: 2, 'z': 3};"
    val result = Terser.minifyToString(
      js,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(keepQuotedProps = true))
    )
    assertEquals(result, "var foo={\"x\":1,y:2,\"z\":3};")
  }

  test("keep_quoted_props: preserve quote styles when quote_style is 3") {
    val js     = "var foo = {\"x\": 1, y: 2, 'z': 3};"
    val result = Terser.minifyToString(
      js,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(keepQuotedProps = true, quoteStyle = 3))
    )
    assertEquals(result, "var foo={\"x\":1,y:2,'z':3};")
  }

  test("keep_quoted_props: not preserved when disabled") {
    val js     = "var foo = {\"x\": 1, y: 2, 'z': 3};"
    val result = Terser.minifyToString(
      js,
      MinifyOptions(compress = false, mangle = false, output = OutputOptions(keepQuotedProps = false, quoteStyle = 3))
    )
    assertEquals(result, "var foo={x:1,y:2,z:3};")
  }

  // 17-18. mangleProperties tests — require mangle.properties, complex

  // 19-22. inSourceMap tests — require source map file I/O

  // 23-25. sourceMapInline tests — require source map encoding

  // 26. "Should return syntax error"
  test("JS_Parse_Error: should return syntax error") {
    val ex = intercept[JsParseError] {
      Terser.minifyToString("function f(a{})", noOpt)
    }
    assert(
      ex.message.contains("{") || ex.message.contains("punc"),
      s"Expected error about unexpected '{', got: ${ex.getMessage}"
    )
  }

  // 27. "Should reject duplicated label name"
  test("JS_Parse_Error: should reject duplicated label name") {
    val ex = intercept[JsParseError] {
      Terser.minifyToString("L:{L:{}}", noOpt)
    }
    assert(
      ex.message.contains("L") && ex.message.contains("defined twice"),
      s"Expected 'Label L defined twice', got: ${ex.getMessage}"
    )
  }

  // 28-29. global_defs tests — require compression

  // 30. "duplicated block-scoped declarations" — parse without error, but compression throws
  test("duplicated block-scoped declarations: parse without error") {
    val cases = List(
      "let a=1;let a=2;",
      "let a=1;var a=2;",
      "var a=1;let a=2;",
      "const a=1;const a=2;",
      "const a=1;var a=2;",
      "var a=1;const a=2;"
    )
    cases.foreach { code =>
      // Should parse without error when compress=false, mangle=false
      val result = Terser.minifyToString(code, MinifyOptions(compress = false, mangle = false))
      assert(result.nonEmpty, s"Expected output for: $code")
    }
  }

  // 31. "should work with compress defaults disabled"
  // Original: minify(code, { compress: { defaults: false } })
  test("should work with compress defaults disabled") {
    val code = "if (true) { console.log(1 + 2); }"
    // compress with defaults:false should not optimize the if(true)
    assertEquals(
      Terser.minifyToString(code, MinifyOptions(compress = CompressorOptions.NoDefaults)),
      "if(true)console.log(1+2);"
    )
  }

  // 32. "should work with compress defaults disabled and evaluate enabled"
  // Original: minify(code, { compress: { defaults: false, evaluate: true } })
  test("should work with compress defaults disabled and evaluate enabled") {
    assertEquals(
      Terser.minifyToString(
        "if (true) { console.log(1 + 2); }",
        MinifyOptions(compress = CompressorOptions.NoDefaults.copy(evaluate = true))
      ),
      "if(true)console.log(3);"
    )
  }

  // 33-36. enclose tests — ported (ISS-1045); see the enclose tests below.

  // 37. "for-await-of should fail in invalid contexts"
  test("for-await-of: valid in async function") {
    val validCases = List(
      "async function f(x){ for await (e of x) {} }",
      "async function f(x){ for await (const e of x) {} }",
      "async function f(x){ for await (var e of x) {} }",
      "async function f(x){ for await (let e of x) {} }"
    )
    validCases.foreach { code =>
      val result = Terser.minifyToString(code, noOpt)
      assert(result.nonEmpty, s"Expected valid parse for: $code")
    }
  }

  test("for-await-of: invalid outside async function") {
    val invalidCases = List(
      "for await(e of x){}",
      "for await(const e of x){}",
      "function f(x){ for await (e of x) {} }",
      "function f(x){ for await (var e of x) {} }",
      "function f(x){ for await (const e of x) {} }",
      "function f(x){ for await (let e of x) {} }"
    )
    invalidCases.foreach { code =>
      intercept[JsParseError] {
        Terser.minifyToString(code, noOpt)
      }
    }
  }

  test("for-await-of: invalid forms in async function") {
    val invalidCases = List(
      "async function f(x){ for await (const e in x) {} }",
      "async function f(x){ for await (;;) {} }"
    )
    invalidCases.foreach { code =>
      intercept[JsParseError] {
        Terser.minifyToString(code, noOpt)
      }
    }
  }

  // 3. "Should skip inherited keys from files" — JS-specific Object.create() multi-file API, not applicable

  // 4. "Should not mutate options" — Scala uses immutable case classes, but verify explicitly
  test("should not mutate options") {
    val options       = MinifyOptions(compress = false, mangle = false, output = OutputOptions(beautify = true))
    val optionsBefore = options.toString
    Terser.minifyToString("x()", options)
    assertEquals(options.toString, optionsBefore, "Options should not be mutated")
  }

  // 5. "Should not mutate options, BUT mutate the nameCache" (minify.js:354-358)
  // Original asserts nameCache.vars.props has key "$a_var" and nameCache.props.props
  // has key "$a_prop". ssg-js property mangling keys the cache by the original
  // (unprefixed) name; we assert the caches are populated with the var/prop names.
  test("nameCache: should mutate nameCache") {
    val cache = new NameCache()
    val opts  = MinifyOptions(
      compress = false,
      mangle = ManglerOptions(toplevel = true, properties = true),
      toplevel = true,
      nameCache = cache
    )
    Terser.minifyToString("const a_var = { a_prop: 'long' }", opts)
    assert(cache.vars.props.nonEmpty, s"vars cache should be populated: ${cache.vars.props}")
    assert(cache.props.props.contains("a_prop"), s"props cache should contain a_prop: ${cache.props.props}")
  }

  // 6. "Should be able to use a dotted property to reach nameCache" — same as #5
  // since the NameCache is the same object the caller passed in.
  test("nameCache: should be reachable via the same NameCache object") {
    val cache = new NameCache()
    val opts  = MinifyOptions(
      compress = false,
      mangle = ManglerOptions(toplevel = true, properties = true),
      toplevel = true,
      nameCache = cache
    )
    Terser.minifyToString("const a_var = { a_prop: 'long' }", opts)
    assert(opts.nameCache != null)
    assert(cache.props.props.contains("a_prop"), s"props cache should contain a_prop: ${cache.props.props}")
  }

  // 9. "Should work with mangle.cache" (minify.js:85-116) — mangle.cache
  // accumulates mangled var names across calls. Ported as an inline differential
  // (the upstream test reads issue-1242 fixture files; the behavior under test is
  // cache accumulation, which we exercise directly).
  test("mangle cache: should work with mangle.cache (accumulates across calls)") {
    val cache  = new ManglerCache()
    val mangle = ManglerOptions(toplevel = true, cache = cache)
    val opts   = MinifyOptions(compress = false, mangle = mangle, toplevel = true)
    Terser.minifyToString("function helper(x){ return 3*x; }", opts)
    assert(cache.props.contains("helper"), s"cache should record helper: ${cache.props}")
    val mapped = cache.props("helper")
    val out2   = Terser.minifyToString("helper(3);", opts)
    assert(out2.contains(mapped), s"second call reuses '$mapped': $out2")
  }

  // 10. "Should work with nameCache" (minify.js:118-149) — nameCache.vars
  // accumulates mangled var names across calls.
  test("nameCache: should work with nameCache (vars persist)") {
    val cache  = new NameCache()
    val mangle = ManglerOptions(toplevel = true)
    val opts   = MinifyOptions(compress = false, mangle = mangle, toplevel = true, nameCache = cache)
    Terser.minifyToString("function helper(x){ return 3*x; }", opts)
    assert(cache.vars.props.contains("helper"), s"nameCache.vars should record helper: ${cache.vars.props}")
    val mapped = cache.vars.props("helper")
    val out2   = Terser.minifyToString("helper(3);", opts)
    assert(out2.contains(mapped), s"second call reuses '$mapped': $out2")
  }

  // 11. "Should avoid mangled names in cache" (minify.js:151-187) — names already
  // present in the property cache are avoided when mangling new properties.
  test("nameCache: should avoid mangled names in cache (no collision across calls)") {
    val cache  = new NameCache()
    val mangle = ManglerOptions(toplevel = true, properties = true)
    val opts   = MinifyOptions(compress = false, mangle = mangle, toplevel = true, nameCache = cache)
    Terser.minifyToString("var i = { prop1: 1 };", opts)
    val firstMappings = cache.props.props.values.toSet
    Terser.minifyToString("var j = { prop2: 2, prop3: 3 };", opts)
    // The first call's mangled prop name must remain in the cache (not overwritten)
    // and new props get distinct names.
    assert(cache.props.props.contains("prop1"), s"prop1 retained: ${cache.props.props}")
    assert(cache.props.props.contains("prop2"), s"prop2 added: ${cache.props.props}")
    val prop1Mapped = cache.props.props("prop1")
    assert(firstMappings.contains(prop1Mapped), "prop1 mapping stable across calls")
  }

  // 12. "Should consistently rename properties colliding with a mangled name"
  // (minify.js:189-220). The four snippets minify through one SHARED nameCache
  // (reused across calls); `obj.i` must consistently rename to `n.o` because the
  // mangled var `n` collides with property `i`. The PROPERTY half is now correct
  // (ISS-1217 resolved: `obj.prop`/`obj.i` mangle to `i`/`o` via the AST_Dot
  // declared-root fix + full domprops reservation). The VARIABLE half is now
  // correct too (ISS-1234 resolved: the second snippet's function `fn2` mangles
  // to `c` — the shared nameCache.vars advances past the `n` used for `fn1`
  // (terser cache.vars: {$fn1:n, $fn2:c}) — via the AST_Toplevel.next_mangled
  // do/while-skip over the cache-seeded mangledNames set, scope.js:736-743/823).
  test("nameCache: should consistently rename properties across calls") {
    val cache = new NameCache()
    val opts  = MinifyOptions(
      compress = false,
      mangle = ManglerOptions(toplevel = true, properties = true),
      toplevel = true,
      nameCache = cache
    )
    val snippets = List(
      "function fn1(obj) { obj.prop = 1; obj.i = 2; }",
      "function fn2(obj) { obj.prop = 1; obj.i = 2; }",
      "let o1 = {}, o2 = {}; fn1(o1); fn2(o2);",
      "console.log(o1.prop === o2.prop, o2.prop === 1, o1.i === o2.i, o2.i === 2);"
    )
    val compressed = snippets.map(Terser.minifyToString(_, opts)).mkString
    assertEquals(
      compressed,
      "function n(n){n.i=1;n.o=2}function c(n){n.i=1;n.o=2}let f={},e={};n(f);c(e);console.log(f.i===e.i,e.i===1,f.o===e.o,e.o===2);"
    )
  }

  // 17. "Shouldn't mangle quoted properties" (minify.js:261-280). With
  // keep_quoted, the quoted keys `"foo"` and `"bar"` must be reserved and left
  // unmangled, while the dotted `color` mangles to `r`.
  test("mangleProperties: should not mangle quoted properties") {
    val js     = "var a = {}; a[\"foo\"] = \"bar\"; a.color = \"red\"; x = {\"bar\": 10};"
    val result = Terser.minifyToString(
      js,
      MinifyOptions(
        compress = CompressorOptions(properties = false),
        mangle = ManglerOptions(properties = PropManglerOptions(builtins = true, keepQuoted = true)),
        output = OutputOptions(keepQuotedProps = true, quoteStyle = 3)
      )
    )
    assertEquals(result, "var a={foo:\"bar\",r:\"red\"};x={\"bar\":10};")
  }

  // 18. "Should not mangle quoted property within dead code" (minify.js:282-292).
  // The quoted `"keep"` in dead code reserves the `keep` name (reserveQuotedKeys
  // runs before compress, so the reservation survives dead-code elimination of
  // `({ "keep": 1 })`), so `g.keep` stays and `g.change` mangles to `g.v`.
  test("mangleProperties: should not mangle quoted property within dead code") {
    val result = Terser.minifyToString(
      "var g = {}; ({ \"keep\": 1 }); g.keep = g.change;",
      MinifyOptions(mangle = ManglerOptions(properties = PropManglerOptions(keepQuoted = true)))
    )
    assertEquals(result, "var g={};g.keep=g.v;")
  }

  // 19. "Should read the given string filename correctly when sourceMapIncludeSources
  //    is enabled (#1236)" (test/mocha/minify.js:296-311). Wires sourceMap.content
  //    (a JSON .map string) + filename + includeSources through Terser.minify
  //    (ISS-1219). The input map is terser/test/input/issue-1236/simple.js.map.
  test("inSourceMap: read filename correctly with includeSources (#1236)") {
    // terser/test/input/issue-1236/simple.js
    val simpleJs =
      "\"use strict\";\n\nvar foo = function foo(x) {\n  return \"foo \" + x;\n};\nconsole.log(foo(\"bar\"));\n\n//# sourceMappingURL=simple.js.map\n"
    // terser/test/input/issue-1236/simple.js.map (read as a JSON string upstream)
    val simpleMap =
      "{\n    \"version\": 3,\n    \"sources\": [\"index.js\"],\n    \"names\": [],\n    \"mappings\": \";;AAAA,IAAI,MAAM,SAAN,GAAM;AAAA,SAAK,SAAS,CAAd;AAAA,CAAV;AACA,QAAQ,GAAR,CAAY,IAAI,KAAJ,CAAZ\",\n    \"file\": \"simple.js\",\n    \"sourcesContent\": [\"let foo = x => \\\"foo \\\" + x;\\nconsole.log(foo(\\\"bar\\\"));\"]\n}\n"
    val result = Terser.minify(
      simpleJs,
      MinifyOptions(
        sourceMap = MinifySourceMapOptions(
          content = simpleMap,
          filename = "simple.min.js",
          includeSources = true
        )
      )
    )
    val map = result.sourceMap
    assert(map != null, "Expected source map output")
    // test/mocha/minify.js:307-310 — map.file, sourcesContent.length, sourcesContent[0].
    assertEquals(map.nn.file, "simple.min.js")
    assertEquals(map.nn.sourcesContent.length, 1)
    assertEquals(
      map.nn.sourcesContent(0),
      "let foo = x => \"foo \" + x;\nconsole.log(foo(\"bar\"));": String | Null
    )
  }

  // 20. "Should process inline source map" (test/mocha/minify.js:312-321). content:
  //    "inline" reads the trailing data-URI map from issue-520/input.js; url: "inline"
  //    appends the output map. The (code + "\n") must equal issue-520/output.js.
  test("inSourceMap: process inline source map") {
    val result = Terser.minify(
      Issue520Input,
      MinifyOptions(
        compress = ssg.js.compress.CompressorOptions(toplevel = ssg.js.compress.ToplevelConfig(funcs = true, vars = true)),
        sourceMap = MinifySourceMapOptions(content = "inline", url = "inline")
      )
    )
    assertEquals(result.code + "\n", Issue520Output)
  }

  // 21. "Should process inline source map (minify_sync)" (test/mocha/minify.js:322-331).
  //    ssg-js is always synchronous; same expectation as #20.
  test("inSourceMap: process inline source map (sync)") {
    val result = Terser.minify(
      Issue520Input,
      MinifyOptions(
        compress = ssg.js.compress.CompressorOptions(toplevel = ssg.js.compress.ToplevelConfig(funcs = true, vars = true)),
        sourceMap = MinifySourceMapOptions(content = "inline", url = "inline")
      )
    )
    assertEquals(result.code + "\n", Issue520Output)
  }

  // 22. "Should fail with multiple input and inline source map" (test/mocha/minify.js:332-346).
  //    minify.js:227-228 — an inline input map with >1 input file throws.
  test("inSourceMap: fail with multiple input and inline source map") {
    val ex = intercept[IllegalArgumentException] {
      Terser.minifySeq(
        List(Issue520Input, Issue520Output),
        MinifyOptions(sourceMap = MinifySourceMapOptions(content = "inline", url = "inline"))
      )
    }
    assertEquals(ex.getMessage, "inline source map only works with singular input")
  }

  // 23. "should append source map to output js when sourceMapInline is enabled"
  //    (test/mocha/minify.js:350-359). url: "inline" appends the data-URI map; the
  //    Base64 is byte-exact with terser's oracle.
  test("sourceMapInline: should append source map to output") {
    val result = Terser.minify(
      "var a = function(foo) { return foo; };",
      MinifyOptions(sourceMap = MinifySourceMapOptions(url = "inline"))
    )
    assertEquals(
      result.code,
      "var a=function(n){return n};\n" +
        "//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJuYW1lcyI6WyJhIiwiZm9vIl0sInNvdXJjZXMiOlsiMCJdLCJtYXBwaW5ncyI6IkFBQUEsSUFBSUEsRUFBSSxTQUFTQyxHQUFPLE9BQU9BLENBQUsifQ=="
    )
  }

  // 24. "should not append source map to output js when sourceMapInline is not enabled"
  test("sourceMapInline: should not append source map without sourceMap option") {
    val result = Terser.minifyToString("var a = function(foo) { return foo; };", noOpt)
    assert(
      !result.contains("sourceMappingURL"),
      s"Expected no source map in output, got: $result"
    )
  }

  // 25. "should work with max_line_len" — requires source map + file I/O
  test("sourceMapInline: max_line_len".fail) {
    fail("Source map inline + file I/O test not yet supported")
  }

  // 28. "Should throw for non-trivial expressions" (global_defs)
  // terser test/mocha/minify.js:397-408 — `@alert` key causes "debugger" to be
  // parsed as an expression; since "debugger" is a statement keyword (not a valid
  // expression), the parser rejects it. Terser expects "Unexpected token: keyword
  // (debugger)"; SSG's parser may phrase the message differently, so we assert
  // that a JsParseError is thrown (the error must propagate, not be swallowed).
  test("global_defs: should throw for non-trivial expressions") {
    // terser test/mocha/minify.js:399-408
    val ex = intercept[JsParseError] {
      Terser.minify(
        "alert(42);",
        MinifyOptions(
          compress = CompressorOptions(
            globalDefs = Map("@alert" -> "debugger")
          )
        )
      )
    }
    // The parse error should mention "debugger" — the unexpected token.
    // terser expects: "Unexpected token: keyword (debugger)"
    assert(ex.message.contains("debugger"), s"Expected parse error about 'debugger', got: ${ex.message}")
  }

  // 29. "Should skip inherited properties" (global_defs)
  // terser test/mocha/minify.js:410-421 — in JS, `Object.create({ skip: this })`
  // creates an object whose prototype has a "skip" property, but whose own
  // properties are only `{ bar: 42 }`. terser's toNode converts only own
  // properties, so the output is `alert({bar:42})`. Scala has no prototype chain,
  // so the "skip inherited" aspect is vacuous; this test verifies that toNode
  // converts a Map (own-property object) to an object literal correctly.
  test("global_defs: should skip inherited properties") {
    // terser test/mocha/minify.js:410-421
    val result = Terser.minify(
      "alert(FOO);",
      MinifyOptions(
        compress = CompressorOptions(
          globalDefs = Map("FOO" -> Map("bar" -> 42.0))
        )
      )
    )
    // terser expects: "alert({bar:42});"
    assertEquals(result.code, "alert({bar:42});")
  }

  // 31. "rename: Should be repeatable" — disabled in original via `if (0)`
  // Original: "rename is disabled on harmony due to expand_names bug in for-of loops"

  // 34-37. enclose tests (minify.js:490-528; ported under ISS-1045).
  private val encloseSrc = "function enclose() {\n    console.log(\"test enclose\");\n}\nenclose();\n"

  test("enclose: should work with true") {
    val result = Terser.minifyToString(encloseSrc, MinifyOptions(compress = false, mangle = false, enclose = true))
    assertEquals(result, "(function(){function enclose(){console.log(\"test enclose\")}enclose()})();")
  }

  test("enclose: should work with arg") {
    val result = Terser.minifyToString(encloseSrc, MinifyOptions(compress = false, mangle = false, enclose = "undefined"))
    assertEquals(result, "(function(undefined){function enclose(){console.log(\"test enclose\")}enclose()})();")
  }

  test("enclose: should work with arg:value") {
    val result = Terser.minifyToString(encloseSrc, MinifyOptions(compress = false, mangle = false, enclose = "window,undefined:window"))
    assertEquals(result, "(function(window,undefined){function enclose(){console.log(\"test enclose\")}enclose()})(window);")
  }

  test("enclose: should work alongside wrap") {
    val result = Terser.minifyToString(
      encloseSrc,
      MinifyOptions(compress = false, mangle = false, enclose = "window,undefined:window", wrap = "exports")
    )
    assertEquals(
      result,
      "(function(window,undefined){(function(exports){function enclose(){console.log(\"test enclose\")}enclose()})(typeof exports==\"undefined\"?exports={}:exports)})(window);"
    )
  }

  // === Additional parse+output tests ===

  test("minify preserves semicolons") {
    assertEquals(Terser.minifyToString("a(); b(); c();", noOpt), "a();b();c();")
  }

  test("minify empty object") {
    assertEquals(Terser.minifyToString("var o = {};", noOpt), "var o={};")
  }

  test("minify nested function") {
    assertEquals(
      Terser.minifyToString("function outer() { function inner() { return 1; } return inner(); }", noOpt),
      "function outer(){function inner(){return 1}return inner()}"
    )
  }

  // ISS-1174 resolved the class/object concise-method parse gap (createAccessor now parses the
  // parameter list); this test passes, so the expected-failure pin was retired.
  test("minify object with methods") {
    val result = Terser.minifyToString("var o = { foo() { return 1; }, bar() { return 2; } };", noOpt)
    assert(result.contains("foo"), s"got: $result")
    assert(result.contains("bar"), s"got: $result")
  }

  // ISS-1039: ecma 5 must NOT emit shorthand — terser oracle confirms:
  //   minify('var {a, b} = obj;', {compress:false,mangle:false,format:{ecma:5}}) → "var{a:a,b:b}=obj;"
  // The previous assertion ({a,b}) encoded the pre-fix bug (output.js:297-298).
  test("minify destructuring assignment") {
    val result = Terser.minifyToString("var {a, b} = obj;", noOpt)
    assertEquals(result, "var{a:a,b:b}=obj;")
  }

  // ISS-1174 resolved the class concise-method parse gap (createAccessor now parses the
  // parameter list); this test passes, so the expected-failure pin was retired.
  test("minify class with non-keyword methods") {
    val result = Terser.minifyToString(
      "class Foo { constructor(x) { this.x = x; } getX() { return this.x; } }",
      noOpt
    )
    assert(result.contains("class Foo"), s"got: $result")
    assert(result.contains("constructor"), s"got: $result")
  }
}
