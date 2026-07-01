/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1238 reproducer — wrap/enclose order relative to compress.
 *
 * terser (minify.js:247 `wrap_commonjs`, :250 `wrap_enclose`) applies the
 * `wrap`/`enclose` IIFE wrapper to the toplevel AST BEFORE the compress pass
 * (minify.js:263) and mangle pass (minify.js:270-279). The whole wrapped
 * program — the injected IIFE wrapper plus its argument — therefore flows
 * through compress, so `1+2` folds to `3`, the inner `var` is dropped, and the
 * inner IIFE is inlined.
 *
 * SSG's Terser.minify currently applies wrap/enclose AFTER compress+mangle, so
 * only the inner body is optimized and the wrapper is emitted un-optimized.
 * That produces different output bytes for the cases below. These tests pin the
 * terser-faithful (oracle-verified) output and are RED until the order is fixed.
 *
 * Oracle (terser ESM entry, original-src/terser/):
 *   node --input-type=module -e "import('./main.js').then(async t=>{const m=t.minify; \
 *     const r=await m('!function(){var x=1+2;console.log(x)}()', \
 *     {compress:true,wrap:'exports'}); console.log(JSON.stringify(r.code));})"
 *   node --input-type=module -e "import('./main.js').then(async t=>{const m=t.minify; \
 *     const r=await m('var x=1+2;console.log(x)', \
 *     {compress:true,enclose:true}); console.log(JSON.stringify(r.code));})"
 */
package ssg
package js

import lowlevel.Nullable

final class WrapEncloseCompressOrderIss1238Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(10, "s")

  // compress = true mirrors the oracle's `{compress:true}` (terser compressor
  // defaults — folds 1+2→3, drops the dead `var`, inlines the IIFE). mangle is
  // disabled so toplevel names (`exports`, `console`) stay verbatim, exactly as
  // the oracle (which passes no mangle key, leaving toplevel names intact) emits.
  private val compressOnly = MinifyOptions(compress = true, mangle = false)

  // Oracle: minify('!function(){var x=1+2;console.log(x)}()',
  //   {compress:true, wrap:'exports'})
  // → "undefined"==typeof exports?exports={}:exports,console.log(3);
  test("ISS-1238: compress+wrap optimizes the whole wrapped program") {
    val result = Terser.minifyToString(
      "!function(){var x=1+2;console.log(x)}()",
      compressOnly.copy(wrap = Nullable("exports"))
    )
    assertEquals(
      result,
      "\"undefined\"==typeof exports?exports={}:exports,console.log(3);"
    )
  }

  // Oracle: minify('var x=1+2;console.log(x)', {compress:true, enclose:true})
  // → console.log(3);
  test("ISS-1238: compress+enclose optimizes the whole enclosed program") {
    val result = Terser.minifyToString(
      "var x=1+2;console.log(x)",
      compressOnly.copy(enclose = true)
    )
    assertEquals(result, "console.log(3);")
  }
}
