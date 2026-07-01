/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

import ssg.commons.Severity
import ssg.minify.html.{ HtmlMinifier, HtmlMinifyOptions }

/** ISS-1376: error-contract wiring for ssg-minify — the `minifyResult` DiagResult facade (docs/architecture/error-contracts.md 2.4).
  *
  * The silent-passthrough degradation (HtmlMinifier returns the input unchanged when compression throws) must surface as a `Severity.Warning` + success — per the section 1.1 severity policy the
  * unoptimized content is still correct output, so it is NOT a degraded/failure result. These differential tests prove a Warning diagnostic IS emitted on a passthrough case and is ABSENT on a clean
  * minify, asserting structure/values (severity, component, code, message, value) rather than non-emptiness.
  */
final class MinifyResultIss1376Suite extends munit.FunSuite {

  /** A JsCompressor that always throws, forcing HtmlMinifier's passthrough degradation. */
  private object FailingJsCompressor extends JsCompressor {
    override def compress(input: String): String =
      throw new RuntimeException("simulated JS compression failure")
  }

  // Inline <script> without src and with non-empty body routes through jsCompressor.compress during doMinify.
  private val htmlWithInlineScript =
    """<html><body><script>var x = 1;</script></body></html>"""

  // Options that enable inline JS compression so the failing compressor is actually invoked.
  private val htmlOpts = HtmlMinifyOptions(compressJsInHtml = true)

  test("ISS-1376: HtmlMinifier.minifyResult emits one Warning diagnostic on passthrough failure") {
    val result = HtmlMinifier.minifyResult(htmlWithInlineScript, htmlOpts, FailingJsCompressor)

    // Warning + success, never degraded or failure (section 1.1 severity policy).
    assert(result.isSuccess, s"expected success, got $result")
    assert(!result.isDegraded, "passthrough must not be degraded")
    assert(!result.isFailure, "passthrough must not be a failure")
    assert(!result.hasErrors, "passthrough must carry no Error diagnostic")

    // Passthrough content is the unchanged input (correct, merely unoptimized).
    assertEquals(result.value, Some(htmlWithInlineScript))

    // Exactly one Warning diagnostic, with the specified structure/values.
    assertEquals(result.diagnostics.size, 1, s"diagnostics: ${result.diagnostics}")
    assertEquals(result.warnings.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Warning)
    assertEquals(d.component, "ssg-minify")
    assertEquals(d.code, Some("html-compression-failed"))
    assert(d.message.contains("HTML compression failed"), s"message: ${d.message}")
    assert(d.message.contains("simulated JS compression failure"), s"message: ${d.message}")
  }

  test("ISS-1376: HtmlMinifier.minifyResult is a clean success (no diagnostics) on successful minify") {
    val input  = "<html><body><p>hello   world</p></body></html>"
    val result = HtmlMinifier.minifyResult(input, HtmlMinifyOptions.Defaults)

    assert(result.isSuccess, s"expected success, got $result")
    assert(result.diagnostics.isEmpty, s"expected no diagnostics, got ${result.diagnostics}")
    assert(result.warnings.isEmpty)
    assert(!result.hasErrors)
    assert(result.value.exists(_.contains("hello world")), s"expected minified output, got ${result.value}")
  }

  test("ISS-1376: Minifier.minifyFileResult routes an .html path through the warning-collecting facade") {
    val opts   = MinifyOptions(html = htmlOpts)
    val result = Minifier.minifyFileResult(htmlWithInlineScript, "index.html", opts, FailingJsCompressor)

    assertEquals(result.value, Some(htmlWithInlineScript))
    assertEquals(result.warnings.size, 1)
    val d = result.warnings.head
    assertEquals(d.severity, Severity.Warning)
    assertEquals(d.component, "ssg-minify")
    assertEquals(d.code, Some("html-compression-failed"))
    assert(result.isSuccess)
    assert(!result.isDegraded)
  }

  test("ISS-1376: Minifier.minifyFileResult clean minify carries no diagnostics") {
    val result = Minifier.minifyFileResult("<html><body><p>hi   there</p></body></html>", "page.html", MinifyOptions.Defaults)

    assert(result.isSuccess, s"expected success, got $result")
    assert(result.diagnostics.isEmpty, s"expected no diagnostics, got ${result.diagnostics}")
    assert(result.value.exists(_.contains("hi there")), s"expected minified output, got ${result.value}")
  }

  test("ISS-1376: Minifier.minifyFileResult copies a .min.js path through as a clean success") {
    val result = Minifier.minifyFileResult("var x = 1 ;", "app.min.js", MinifyOptions.Defaults)

    assertEquals(result.value, Some("var x = 1 ;"))
    assert(result.diagnostics.isEmpty)
    assert(result.isSuccess)
  }
}
