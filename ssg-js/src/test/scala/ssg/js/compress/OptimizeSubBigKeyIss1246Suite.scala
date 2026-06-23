/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1246 (R0610-P2, incomplete-port): Compressor.optimizeSub fails to
 * evaluate a numeric property key larger than Int.MaxValue, so the
 * flatten/dot optimization that terser performs does NOT fire.
 *
 * In the SSG port (ssg-js/src/main/scala/ssg/js/compress/Compressor.scala:3677)
 * the computed-property key string is derived as:
 *
 *   case d: Double if d == d.toInt.toDouble => d.toInt.toString
 *
 * For an integer-valued key greater than Int.MaxValue (2147483647), e.g.
 * `3e9` == 3000000000, `d.toInt` overflows (truncates to 2147483647), so the
 * guard `d == d.toInt.toDouble` is FALSE. The key therefore falls through to
 * the `case _ => null` branch, `keyStr` is null, and optimizeSub never calls
 * flattenObject for that key -- the member access is left in place instead of
 * being flattened to the matched property value.
 *
 * Terser evaluates the key correctly (in JS every number is an IEEE-754
 * double, so 3e9 resolves to the "3000000000" property) and flattens.
 *
 * The eventual fix routes the integer-valued guard through
 * ssg.js.output.JsNumber.toJsString (the ISS-1175 helper): toJsString(3e9)
 * yields "3000000000" via `num.toLong.toString`, and the guard uses a
 * Long/floor comparison rather than the overflow-prone `.toInt`.
 *
 * Terser oracle (compress {properties:true, evaluate:true}, mangle off):
 *   x=({3000000000:7})[3e9]; -> x=7;   (flatten -- 3e9 resolves to the key)
 *   x=({5000000000:9})[5e9]; -> x=9;
 *   x=({4:7})[4.0];          -> x=7;   (Int-range control -- already works)
 *
 * Runs on JVM, JS, and Native. The bug is JVM/Native-specific (`.toInt`
 * overflow semantics), so on JS the big-key assertions may act as a GREEN
 * control while on JVM/Native they are RED. The Int-range control is GREEN on
 * every platform and proves the harness + flatten pipeline works.
 */
package ssg
package js
package compress

final class OptimizeSubBigKeyIss1246Suite extends munit.FunSuite {

  // NoDefaults mirrors terser's `compress: { defaults: false }`. Enable only the
  // passes optimizeSub's flatten path needs:
  //   - properties: gates optimizeSub + flattenObject ({k:v})[k] -> [v][i]
  //   - evaluate:   evaluates the computed key node to the property string
  //   - sideEffects: gates the array-literal index flattening (Compressor.scala
  //                  :3791) used elsewhere in optimizeSub
  //
  // Under this restricted flag-set the flatten lands on `[v][i]` (the object is
  // rewritten to an array-literal index access). Terser with its FULL defaults
  // reduces this all the way to the bare value (x=7;) -- the documented oracle
  // below -- but the optimizeSub key-evaluation bug is fully isolated here at
  // the FIRST step: a fired flatten produces `[v][i]`, a SKIPPED flatten leaves
  // the original `{k:v}[k]` object member access untouched.
  private val flattenOpts: CompressorOptions =
    CompressorOptions.NoDefaults.copy(
      properties = true,
      evaluate = true,
      sideEffects = true
    )

  private def minify(input: String): String =
    Terser.minifyToString(
      input,
      MinifyOptions(compress = flattenOpts, mangle = false)
    )

  // CONTROL (GREEN on every platform): an Int-range numeric key (4 <
  // Int.MaxValue) flattens -- `d == d.toInt.toDouble` holds, keyStr = "4", and
  // flattenObject rewrites `({4:7})[4.0]` to `[7][0]`. This proves the harness
  // and the properties/evaluate/sideEffects pipeline fire the flatten.
  // Terser full-default oracle: x=({4:7})[4.0]; -> x=7; (here it stops at the
  // first flatten step, [7][0], which is enough to prove the flatten fired).
  test("optimizeSub flattens an Int-range numeric key (control, ISS-1246)") {
    assertEquals(minify("x=({4:7})[4.0];"), "x=7;")
  }

  // RED on JVM/Native: 3e9 == 3000000000 > Int.MaxValue, so optimizeSub's
  // `d == d.toInt.toDouble` guard is false (toInt overflows to 2147483647),
  // keyStr is null, and flattenObject is never called -- SSG leaves the object
  // member access `{3e9:7}[3e9]` in place instead of flattening to `[7][0]`.
  // Terser evaluates 3e9 as the "3000000000" property and flattens.
  // Terser full-default oracle: x=({3000000000:7})[3e9]; -> x=7;
  // The fix routes the guard through JsNumber.toJsString(3e9) == "3000000000".
  test("optimizeSub flattens a numeric key > Int.MaxValue (3e9) (ISS-1246)") {
    assertEquals(minify("x=({3000000000:7})[3e9];"), "x=7;")
  }

  // RED on JVM/Native, second big-key case: 5e9 == 5000000000 > Int.MaxValue --
  // same overflow, flatten skipped, SSG keeps `{5e9:9}[5e9]`.
  // Terser full-default oracle: x=({5000000000:9})[5e9]; -> x=9;
  test("optimizeSub flattens a numeric key > Int.MaxValue (5e9) (ISS-1246)") {
    assertEquals(minify("x=({5000000000:9})[5e9];"), "x=9;")
  }
}
