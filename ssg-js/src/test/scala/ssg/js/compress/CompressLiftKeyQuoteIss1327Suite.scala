/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1327 (R0610, bug): Compressor.liftKey drops the key's `quote` when
 * lifting a computed property on a non-KeyVal property (method, getter,
 * setter), so `keep_quoted` later mangles a property key it must preserve.
 *
 * terser `lift_key` (index.js:4003-4016) copies `self.quote = self.key.quote`
 * in ALL THREE branches (KeyVal, ClassProperty, else/method). SSG's liftKey
 * only copied the quote in the KeyVal branch, leaving method/getter/setter
 * with an empty quote. PropMangler's `keepQuoted` guards check
 * `cm.quote.isEmpty` / `og.quote.isEmpty` / `os.quote.isEmpty`, so an
 * empty quote means the key is mangled despite `keep_quoted:true`.
 *
 * Observable: with `keep_quoted:true` and `computed_props+reduce_vars+unused`,
 * a computed method key `[prop]()` lifted to `_foo()` gets mangled to `o()`,
 * while `id("_foo")` preserves the string access -> runtime TypeError.
 *
 * Terser oracle (node original-src/terser/dist/bundle.min.js):
 *   Case A (method):  let o={_foo(){return"bar"}};console.log(o[id("_foo")]());
 *   Case B (getter):  let o={get _foo(){return"bar"}};console.log(o[id("_foo")]);
 *   Case C (keyval):  let o={_foo:"bar"};console.log(o[id("_foo")]);
 */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.scope.{ ManglerOptions, PropManglerOptions }

final class CompressLiftKeyQuoteIss1327Suite extends munit.FunSuite {

  // reduce_vars + unused inline the `let prop = "_foo"` reference, then
  // computed_props lifts the now-constant computed key [prop] -> _foo.
  // mangle.properties.keep_quoted must then PRESERVE _foo because it
  // appeared as a quoted-string access in `id("_foo")`.
  private val opts: MinifyOptions = MinifyOptions(
    compress = CompressorOptions.NoDefaults.copy(
      reduceVars = true,
      unused = true,
      computedProps = true
    ),
    mangle = ManglerOptions(
      properties = PropManglerOptions(keepQuoted = true)
    ),
    toplevel = true
  )

  private def minify(input: String): String =
    Terser.minifyToString(input, opts)

  // Case A: concise method — the `case _ =>` branch in liftKey.
  // Without the quote copy, SSG mangles _foo -> o, producing {o(){...}}.
  // terser oracle: let o={_foo(){return"bar"}};console.log(o[id("_foo")]());
  test("liftKey preserves quote on computed concise method key (ISS-1327)") {
    val input    = """let prop = "_foo"; let o = { [prop]() { return "bar" } }; console.log(o[id("_foo")]());"""
    val expected = """let o={_foo(){return"bar"}};console.log(o[id("_foo")]());"""
    assertEquals(minify(input), expected)
  }

  // Case B: getter — also the `case _ =>` branch in liftKey.
  // Without the quote copy, SSG mangles _foo -> o, producing {get o(){...}}.
  // terser oracle: let o={get _foo(){return"bar"}};console.log(o[id("_foo")]);
  test("liftKey preserves quote on computed getter key (ISS-1327)") {
    val input    = """let prop = "_foo"; let o = { get [prop]() { return "bar" } }; console.log(o[id("_foo")]);"""
    val expected = """let o={get _foo(){return"bar"}};console.log(o[id("_foo")]);"""
    assertEquals(minify(input), expected)
  }

  // Case C: keyval — the KeyVal branch already copies quote (regression guard).
  // terser oracle: let o={_foo:"bar"};console.log(o[id("_foo")]);
  test("liftKey preserves quote on computed keyval key — regression guard (ISS-1327)") {
    val input    = """let prop = "_foo"; let o = { [prop]: "bar" }; console.log(o[id("_foo")]);"""
    val expected = """let o={_foo:"bar"};console.log(o[id("_foo")]);"""
    assertEquals(minify(input), expected)
  }
}
