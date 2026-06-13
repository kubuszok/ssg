/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for ISS-1037: pure_getters truthiness under the DEFAULT
 * setting ("strict").
 *
 * ISS-1037 as stated claimed the default `pure_getters` value (the String
 * "strict", CompressorOptions.scala:183,274) made `optionBool("pure_getters")`
 * return `false` (because the old optionBool treated any String as falsy),
 * while JS truthiness of the non-empty String "strict" is `true`. That would
 * invert every property-access side-effect guard relative to terser
 * (`may_throw_on_access` = `!compressor.option("pure_getters") ||
 * this._dot_throw(compressor)`, inference.js:751-754; port dotThrow =
 * `!compressor.optionBool("pure_getters") || mayThrowOnAccess(...)`,
 * Inference.scala:822).
 *
 * ISS-1037 adjudicated stale: ISS-1141's JS-truthiness optionBool
 * (CompressorLike.scala:50-62 — a non-empty String is truthy, so
 * optionBool("pure_getters") is `true` for "strict") + the pre-existing
 * `/strict/`-equivalent site (Inference.isStrict, Inference.scala:744-746 uses
 * `.contains("strict")`, matching `/strict/.test(...)` at inference.js:757) and
 * the `== true` sites (Compressor.scala:4562, DropUnused.scala:848 — the literal
 * boolean `true`, NOT "strict", matching index.js:4077 and drop-unused.js:331)
 * already match terser pure_getters semantics. This differential pins it.
 *
 * Site-by-site (port vs terser):
 *   - dotThrow: `!optionBool("pure_getters") || mayThrowOnAccess(...)`
 *       == terser may_throw_on_access `!option(...) || _dot_throw(...)` (truthiness)
 *   - isStrict: `option(...).contains("strict")`
 *       == terser is_strict `/strict/.test(option(...))`
 *   - destructuring prune: `option("pure_getters") == true`
 *       == terser index.js:4077 `== true` (literal boolean, NOT "strict")
 *   - drop-unused keep: `option("pure_getters") != true`
 *       == terser drop-unused.js:331 `!= true`
 *
 * Oracle: terser 5.46.1, node v24.12.0, run 2026-06-13
 *   cd original-src/terser; node --input-type=module -e
 *   "import {minify} from './main.js'; const r=await minify(CODE,
 *   {compress:OPTS,mangle:false}); console.log(JSON.stringify(r.code))"
 *   with OPTS = `true` (default => pure_getters "strict"):
 *   - 'function f(){var x = {a:1}.a; return 1;}' -> "function f(){return 1}"
 *       (safe object-literal access, no getter: strict allows dropping; only
 *        possible because optionBool("strict") is truthy => `!truthy` is false)
 *   - 'function f(o){var x = o.p; return 1;}'    -> "function f(o){o.p;return 1}"
 *       (unknown member under strict: dot may throw, access kept)
 *   - 'function f(){var x = "str".length; return 1;}' -> "function f(){return 1}"
 *       (constant access is never a throw site, dropped)
 *   - 'function f(o){var {a:x, b:y} = o; return x;}' -> "function f(o){var{a:x,b:y}=o;return x}"
 *       (destructuring prune is `== true`-only: default "strict" must NOT prune
 *        b:y; renamed keys are used so the output is free of the ES6
 *        property-shorthand printing difference. With OPTS = `{pure_getters:true}`
 *        terser prunes to "function f(o){var{a:x}=o;return x}", confirming the
 *        prune is genuinely `== true`-gated, not truthiness-gated.)
 */
package ssg
package js

import ssg.js.compress.CompressorOptions

final class PureGettersStrictIss1037Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // DEFAULT compress options: explicit CompressorOptions() carries
  // pureGetters = "strict" (CompressorOptions.scala:183), the same default
  // terser uses (`pure_getters : !false_by_default && "strict"`,
  // index.js:258). mangle = false isolates the relevant passes.
  private def default = MinifyOptions(compress = CompressorOptions(), mangle = false)

  test("ISS-1037: safe object-literal property access is dropped under default 'strict'") {
    // Requires optionBool("strict") to be TRUTHY: dotThrow is
    // `!optionBool || mayThrowOnAccess`; if optionBool were falsy (the alleged
    // bug) `!false` would be true and the access would always be kept.
    val out = Terser.minifyToString("function f(){var x = {a:1}.a; return 1;}", default)
    assertEquals(out, "function f(){return 1}", "safe object-literal access must drop under default 'strict' (terser 5.46.1)")
  }

  test("ISS-1037: unknown member access is kept under default 'strict'") {
    // is_strict (`.contains(\"strict\")`) makes an unknown AST_Dot may-throw,
    // so the side-effecting access is preserved.
    val out = Terser.minifyToString("function f(o){var x = o.p; return 1;}", default)
    assertEquals(out, "function f(o){o.p;return 1}", "unknown member access must be kept under default 'strict' (terser 5.46.1)")
  }

  test("ISS-1037: constant property access is dropped under default 'strict'") {
    val out = Terser.minifyToString("function f(){var x = \"str\".length; return 1;}", default)
    assertEquals(out, "function f(){return 1}", "constant access is never a throw site and must drop (terser 5.46.1)")
  }

  test("ISS-1037: destructuring prune does NOT fire under default 'strict'") {
    // index.js:4077 gates the prune on `pure_getters == true` (the literal
    // boolean), NOT on truthiness, so the default "strict" must keep `b`.
    val out = Terser.minifyToString("function f(o){var {a:x, b:y} = o; return x;}", default)
    assertEquals(
      out,
      "function f(o){var{a:x,b:y}=o;return x}",
      "destructuring prune must NOT fire under default 'strict' (terser 5.46.1)"
    )
  }
}
