/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for ISS-1177: the destructuring-prune path in
 * Compressor.optimizeDestructuring (Compressor.scala:4589, terser
 * index.js:4077) is gated on `compressor.option("pure_getters") == true`
 * (the literal boolean `true`). Before the fix, CompressorOptions.pureGetters
 * was typed `String`, so `option("pure_getters")` could NEVER return the
 * boolean `true` under ANY config — making the prune dead code. ISS-1177
 * widens the field to `Any` (mirroring keepFnames, cf. ISS-1040 keep_fnames),
 * so a user setting `pure_getters = true` reaches the boolean-gated path.
 *
 * This suite proves the prune FIRES when pure_getters = true and matches the
 * terser oracle, while the default "strict" still does NOT prune (the green
 * guard from ISS-1037).
 *
 * Oracle: terser 5.46.1, node v24.12.0, run 2026-06-16
 *   cd original-src/terser; node --input-type=module -e
 *   "import {minify} from './main.js';
 *    const SRC = 'function f(o){var {a:x, b:y} = o; return x;}';
 *    for (const pg of [true, 'strict']) {
 *      const r = await minify(SRC, { compress: { pure_getters: pg }, mangle: false });
 *      console.log(JSON.stringify(r.code));
 *    }"
 *   =>
 *   pure_getters=true     => "function f(o){var{a:x}=o;return x}"   (b:y PRUNED)
 *   pure_getters="strict" => "function f(o){var{a:x,b:y}=o;return x}" (b:y KEPT)
 *
 * Renamed keys (a:x / b:y) avoid the ES6 property-shorthand printing
 * difference so the only observable change is the prune of the unused binding.
 */
package ssg
package js

import ssg.js.compress.CompressorOptions

final class PureGettersDestructuringIss1177Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // Same source as the ISS-1037 green guard; the only variable is the value of
  // pure_getters. mangle = false isolates the destructuring-prune pass.
  private val src = "function f(o){var {a:x, b:y} = o; return x;}"

  test("ISS-1177: destructuring prune FIRES when pure_getters = true") {
    // Requires option("pure_getters") to equal the boolean `true`. With the
    // pre-fix `String` field this branch (Compressor.scala:4589) was dead code,
    // so `b:y` would be kept and this assertion would fail (red without fix).
    val opts = MinifyOptions(compress = CompressorOptions(pureGetters = true), mangle = false)
    val out  = Terser.minifyToString(src, opts)
    assertEquals(
      out,
      "function f(o){var{a:x}=o;return x}",
      "destructuring prune must fire under pure_getters = true (terser 5.46.1)"
    )
  }

  test("ISS-1177: destructuring prune does NOT fire under default 'strict'") {
    // Green guard (ISS-1037): the default String "strict" must NOT equal the
    // boolean `true`, so the prune does not run and `b:y` is preserved.
    val opts = MinifyOptions(compress = CompressorOptions(), mangle = false)
    val out  = Terser.minifyToString(src, opts)
    assertEquals(
      out,
      "function f(o){var{a:x,b:y}=o;return x}",
      "destructuring prune must NOT fire under default 'strict' (terser 5.46.1)"
    )
  }
}
