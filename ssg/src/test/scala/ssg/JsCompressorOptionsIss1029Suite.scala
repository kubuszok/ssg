/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1029: jsCompressorOpts was an unfillable dead API — JsCompressorOptions
 * was an empty trait with no implementations, the 2-arg compress discarded its
 * options, and inline-script compression never threaded options. This suite
 * proves the option type now flows end-to-end through BOTH the standalone-JS
 * path (Minifier.compress / Minifier.minify(FileType.Js)) and the inline-<script>
 * path (HtmlMinifier via Minifier.minifyHtml), so a JS-compressor option
 * (mangle on vs off) measurably changes the output.
 *
 * Expected outputs cited from the vendored terser oracle (BSD-2-Clause):
 *   cd original-src/terser && node --input-type=module -e '
 *     import { minify } from "./main.js";
 *     const src = "function add(firstNumber, secondNumber) { var resultValue = firstNumber + secondNumber; return resultValue; }";
 *     console.log((await minify(src, { compress: false, mangle: true  })).code);
 *     console.log((await minify(src, { compress: false, mangle: false })).code);'
 *   mangle:true  => function add(n,r){var a=n+r;return a}
 *   mangle:false => function add(firstNumber,secondNumber){var resultValue=firstNumber+secondNumber;return resultValue}
 */
package ssg

import ssg.minify.{ FileType, MinifyOptions, Minifier }
import ssg.minify.html.{ HtmlMinifier, HtmlMinifyOptions }

class JsCompressorOptionsIss1029Suite extends munit.FunSuite {

  // A function whose parameter and local names mangle distinctly. Compression is
  // disabled so the ONLY difference between the two runs is name mangling.
  private val js =
    "function add(firstNumber, secondNumber) { var resultValue = firstNumber + secondNumber; return resultValue; }"

  private val mangleOn  = TerserJsCompressorOptions(compress = false, mangle = true)
  private val mangleOff = TerserJsCompressorOptions(compress = false, mangle = false)

  // ── Standalone-JS path ────────────────────────────────────────────────────

  test("standalone JS: mangle option flows through the adapter's 2-arg compress") {
    val withMangle    = TerserJsCompressorAdapter.compress(js, mangleOn)
    val withoutMangle = TerserJsCompressorAdapter.compress(js, mangleOff)

    // The original long names survive only when mangling is OFF.
    assert(
      withoutMangle.contains("firstNumber") && withoutMangle.contains("resultValue"),
      s"expected original names with mangle off, got: $withoutMangle"
    )
    assert(
      !withMangle.contains("firstNumber") && !withMangle.contains("resultValue"),
      s"expected mangled names with mangle on, got: $withMangle"
    )
    // Differential: the option actually changes the output.
    assertNotEquals(withMangle, withoutMangle)
  }

  test("standalone JS: option flows through Minifier.minify(FileType.Js)") {
    val optsOn  = MinifyOptions(jsCompressorOpts = Some(mangleOn))
    val optsOff = MinifyOptions(jsCompressorOpts = Some(mangleOff))

    val outOn  = Minifier.minify(js, FileType.Js, optsOn, TerserJsCompressorAdapter)
    val outOff = Minifier.minify(js, FileType.Js, optsOff, TerserJsCompressorAdapter)

    assert(outOff.contains("firstNumber"), s"expected unmangled names, got: $outOff")
    assert(!outOn.contains("firstNumber"), s"expected mangled names, got: $outOn")
    assertNotEquals(outOn, outOff)
  }

  // ── Inline-<script> path (HtmlMinifier) ───────────────────────────────────

  private val htmlOpts =
    HtmlMinifyOptions(compressJsInHtml = true)

  private val html =
    s"<html><head><script>$js</script></head><body></body></html>"

  test("inline <script>: mangle option flows through HtmlMinifier.minify") {
    val outOn = HtmlMinifier.minify(html, htmlOpts, TerserJsCompressorAdapter, Some(mangleOn))
    val outOff = HtmlMinifier.minify(html, htmlOpts, TerserJsCompressorAdapter, Some(mangleOff))

    assert(outOff.contains("firstNumber"), s"expected unmangled inline JS, got: $outOff")
    assert(!outOn.contains("firstNumber"), s"expected mangled inline JS, got: $outOn")
    assertNotEquals(outOn, outOff)
  }

  test("inline <script>: option flows through Minifier.minifyHtml") {
    val optsOn = MinifyOptions(html = htmlOpts, jsCompressorOpts = Some(mangleOn))
    val optsOff = MinifyOptions(html = htmlOpts, jsCompressorOpts = Some(mangleOff))

    val outOn  = Minifier.minifyHtml(html, optsOn, TerserJsCompressorAdapter)
    val outOff = Minifier.minifyHtml(html, optsOff, TerserJsCompressorAdapter)

    assert(outOff.contains("firstNumber"), s"expected unmangled inline JS, got: $outOff")
    assert(!outOn.contains("firstNumber"), s"expected mangled inline JS, got: $outOn")
    assertNotEquals(outOn, outOff)
  }

  test("inline <script>: option also flows through Minifier.minify(FileType.Html)") {
    val optsOn = MinifyOptions(html = htmlOpts, jsCompressorOpts = Some(mangleOn))

    val outOn = Minifier.minify(html, FileType.Html, optsOn, TerserJsCompressorAdapter)
    assert(!outOn.contains("firstNumber"), s"expected mangled inline JS, got: $outOn")
  }

  // ── Oracle-cited exact output (standalone) ─────────────────────────────────

  test("standalone JS: exact mangled body matches the terser oracle") {
    // Oracle (compress:false, mangle:true): function add(n,r){var a=n+r;return a}
    val out = TerserJsCompressorAdapter.compress(js, mangleOn)
    assertEquals(out, "function add(n,r){var a=n+r;return a}")
  }

  test("standalone JS: exact unmangled body matches the terser oracle") {
    // Oracle (compress:false, mangle:false):
    //   function add(firstNumber,secondNumber){var resultValue=firstNumber+secondNumber;return resultValue}
    val out = TerserJsCompressorAdapter.compress(js, mangleOff)
    assertEquals(
      out,
      "function add(firstNumber,secondNumber){var resultValue=firstNumber+secondNumber;return resultValue}"
    )
  }
}
