/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for ISS-1036: IIFE-return inlining must fire at the DEFAULT
 * inline level.
 *
 * ISS-1036 as stated claimed `optionBool("inline")` (Inline.scala) always yields
 * `false` for the Int-valued `inline` option, so IIFE-return inlining could never
 * fire. That root cause was already fixed by ISS-1141 (iteration 28), which
 * reworked `CompressorLike.optionBool` (CompressorLike.scala:50-62) to model JS
 * truthiness: a non-zero Int is truthy. `Inline.scala:516` gates IIFE-return
 * inlining on `compressor.optionBool("inline")`, and the inline LEVEL is read as
 * an Int at Inline.scala:614,760. The port's default `inline` is
 * `InlineLevel.InlineFull` (level 3, CompressorOptions.scala:157), surfaced as the
 * Int `3` via `option("inline") => inline.level` (CompressorOptions.scala:261).
 * terser's default is `inline: !false_by_default` i.e. `true`, normalized to `3`
 * (lib/compress/index.js:246,290). Both defaults agree on level 3.
 *
 * This differential pins the port's output against terser's at the default level,
 * proving IIFE-return inlining fires. Oracle: terser 5.46.1, node v24.12.0, run
 * 2026-06-13 (cd original-src/terser; node --input-type=module -e
 * "import {minify} from './main.js'; const r=await minify(CODE,
 * {compress:true,mangle:false}); console.log(JSON.stringify(r.code))"):
 *   - 'var y = (function(){ return 42; })();'      -> "var y=42;"
 *   - 'var z = (function(a){ return a; })(7);'     -> "var z=7;"
 *   - 'var w = (function(){ return 1 + 2; })();'   -> "var w=3;"
 */
package ssg
package js

import ssg.js.compress.CompressorOptions

final class IifeInlineLevelIss1036Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // DEFAULT compress options: explicit CompressorOptions() carries inline =
  // InlineLevel.InlineFull (level 3), the same default level terser uses
  // (lib/compress/index.js:246,290). mangle = false isolates the inlining pass.
  private def default = MinifyOptions(compress = CompressorOptions(), mangle = false)

  test("ISS-1036: IIFE returning a constant inlines at the default level") {
    val out = Terser.minifyToString("var y = (function(){ return 42; })();", default)
    assertEquals(out, "var y=42;", "IIFE-return inlining must fire at the default inline level (terser 5.46.1)")
  }

  test("ISS-1036: IIFE returning its argument inlines at the default level") {
    val out = Terser.minifyToString("var z = (function(a){ return a; })(7);", default)
    assertEquals(out, "var z=7;", "IIFE-return inlining of an argument must fire at the default inline level (terser 5.46.1)")
  }

  test("ISS-1036: IIFE returning a folded expression inlines at the default level") {
    val out = Terser.minifyToString("var w = (function(){ return 1 + 2; })();", default)
    assertEquals(out, "var w=3;", "IIFE-return inlining + constant folding must fire at the default inline level (terser 5.46.1)")
  }
}
