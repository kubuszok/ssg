/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1245 (R0610-P2, incomplete-port): Compressor.liftKey formats a lifted
 * numeric computed-property key with Scala's `.toString`, not the JS number
 * form.
 *
 * The `lift_key` pass converts a computed property whose key is a numeric
 * literal into a plain numeric key: `{[4]:1}` -> `{4:1}`. In the SSG port
 * (ssg-js/src/main/scala/ssg/js/compress/Compressor.scala:4459) the lifted
 * key is computed as `val key = num.value.toString`. `num.value` is a Double,
 * and on JVM/Native `(4.0).toString == "4.0"` and `3e9` stringifies as
 * "3.0E9" -- NOT the JavaScript number form. Terser instead lifts the key
 * via JS implicit Number->String coercion, which yields "4" and "3e9".
 *
 * Because the lifted key string "4.0" / "3.0E9" is neither an integer-valued
 * key (OutputStream.printPropertyName's `asNum.toLong.toString == key` check
 * fails: "4" != "4.0") nor a valid identifier, OutputStream emits it as a
 * QUOTED string key. So SSG produces `{"4.0":1}` / `{"3.0E9":1}` where terser
 * produces `{4:1}` / `{3e9:1}`.
 *
 * The eventual fix replaces `num.value.toString` with
 * `ssg.js.output.JsNumber.toJsString(num.value)` (the same JS-faithful helper
 * introduced for ISS-1175), making the lifted key match terser.
 *
 * Terser oracle (compress {computed_props:true}, mangle off):
 *   x={[4]:1};   -> x={4:1};
 *   x={[4.0]:1}; -> x={4:1};
 *   x={[3e9]:1}; -> x={3e9:1};
 *
 * Runs on JVM, JS, and Native. The bug is JVM/Native-specific (JS's own
 * `.toString` already matches the JS form), so on JS these assertions act as a
 * GREEN control while on JVM/Native they are RED.
 */
package ssg
package js
package compress

final class LiftKeyNumericKeyIss1245Suite extends munit.FunSuite {

  // NoDefaults mirrors terser's `compress: { defaults: false }` -- every
  // default-gated pass is off. Enable ONLY computedProps (the SSG name for
  // terser's `computed_props`) so lift_key is the sole transform exercised.
  private val computedPropsOnly: CompressorOptions =
    CompressorOptions.NoDefaults.copy(computedProps = true)

  private def minify(input: String): String =
    Terser.minifyToString(
      input,
      MinifyOptions(compress = computedPropsOnly, mangle = false)
    )

  // RED on JVM/Native: liftKey lifts `[4]` via `num.value.toString` -> "4.0",
  // which OutputStream then emits as the quoted key `"4.0"`, giving
  // `var x={"4.0":1};`. Terser lifts it as the JS number "4" -> `var x={4:1};`.
  test("liftKey lifts an integer-valued computed key as the JS number 4 (ISS-1245)") {
    assertEquals(minify("var x={[4]:1};"), "var x={4:1};")
  }

  // RED on JVM/Native: `[3e9]` lifts via `(3e9).toString` -> "3.0E9", emitted
  // as the quoted key `"3.0E9"`, giving `var x={"3.0E9":1};`. Terser lifts it
  // as the JS exponential form "3e9" -> `var x={3e9:1};`.
  test("liftKey lifts a large computed key as the JS number 3e9 (ISS-1245)") {
    assertEquals(minify("var x={[3e9]:1};"), "var x={3e9:1};")
  }

  // Distinguisher guards (robust to any output-wrapper differences): the lifted
  // key must be the bare JS number, never the Scala `.toString` form.
  test("lifted numeric keys never carry the Scala .toString form (ISS-1245)") {
    val out4 = minify("var x={[4]:1};")
    assert(out4.contains("4:"), s"expected a bare `4:` key, got: $out4")
    assert(!out4.contains("4.0"), s"key must not be the Scala `(4.0).toString` form, got: $out4")

    val outBig = minify("var x={[3e9]:1};")
    assert(outBig.contains("3e9:"), s"expected a bare `3e9:` key, got: $outBig")
    assert(!outBig.contains("3.0E9"), s"key must not be the Scala `(3e9).toString` form, got: $outBig")
  }
}
