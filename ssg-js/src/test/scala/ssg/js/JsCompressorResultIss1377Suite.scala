/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package js

import ssg.commons.Severity

/** Differential tests for the ISS-1377 error-contract facade `TerserJsCompressor.compressResult` (docs/architecture/error-contracts.md §2.5, second adapter).
  *
  * `compressResult` is the additive `DiagResult` envelope over the existing `TerserJsCompressor.compress`, built with the SAME collecting-logger technique as ssg-minify's `HtmlMinifier.minifyResult`
  * (§2.5 lines 283-287): it passes a buffering `ssg.commons.Logger` into the unchanged `compress` and maps each warned message into a
  * `Diagnostic.warning("ssg-js", msg, code = Some("js-compression-failed"))`. Per the §1.1 severity policy the return-input-unchanged degradation is a Warning + success (the passthrough content is
  * still correct JS, merely uncompressed) — NEVER a failure or degraded result. No new catch is introduced: the ISS-1052 compress + catch + Logger channel stays intact.
  */
final class JsCompressorResultIss1377Suite extends munit.FunSuite {

  // Syntactically invalid JavaScript: the missing closing paren makes Terser.minifyToString throw a
  // JsParseError, which the existing compress catch turns into a logger.warn (the ISS-1052 warn path).
  private val invalidJs = "function f(a{}"
  // Valid JavaScript for the success path.
  private val validJs = "var x = 1 + 2;"

  test("ISS-1377: compressResult is a clean success carrying the same compressed string as compress") {
    val legacy = TerserJsCompressor.compress(validJs)
    val result = TerserJsCompressor.compressResult(validJs)

    assert(result.isSuccess, s"a clean compress must be a success, got $result")
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.diagnostics, Vector.empty)
    assertEquals(result.value, Some(legacy))
  }

  test("ISS-1377: compressResult surfaces the ISS-1052 warn path as a Warning + success") {
    val result = TerserJsCompressor.compressResult(invalidJs)

    // Warning + success, never degraded or failure (§1.1 severity policy).
    assert(result.isSuccess, s"passthrough must be a success, got $result")
    assert(!result.isDegraded, "passthrough must not be degraded")
    assert(!result.isFailure, "passthrough must not be a failure")
    assert(!result.hasErrors, "passthrough must carry no Error diagnostic")

    // Graceful degradation: the original input is returned unchanged (same as compress).
    assertEquals(result.value, Some(invalidJs))
    assertEquals(result.value, Some(TerserJsCompressor.compress(invalidJs)))

    // Exactly one Warning diagnostic with the specified structure/values.
    assertEquals(result.diagnostics.size, 1, s"diagnostics: ${result.diagnostics}")
    assertEquals(result.warnings.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Warning)
    assertEquals(d.component, "ssg-js")
    assertEquals(d.code, Some("js-compression-failed"))
    assertEquals(d.position, None)
    // The message is the compress channel's warn text verbatim.
    assert(d.message.contains("JS compression failed"), s"message: ${d.message}")
    assert(d.message.contains("JsParseError"), s"message: ${d.message}")
    assert(d.message.contains("Using original source"), s"message: ${d.message}")
  }
}
