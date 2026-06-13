/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

import ssg.minify.html.{ HtmlMinifier, HtmlMinifyOptions }

/** ISS-1028: HtmlMinifier.minify silently swallows exceptions with no diagnostics.
  *
  * jekyll-minifier.rb:1013-1015 rescues and warns via `Jekyll.logger.warn("Jekyll Minifier:", "HTML compression failed for ...: #{e.message}. Using original content.")` before degrading. The SSG port
  * must do the same via an injectable `Logger` channel.
  *
  * Test approach: inject a `JsCompressor` test double that always throws, pass HTML with an inline `<script>` block so `compressInlineJs` invokes the test double during `doMinify`, and verify that
  * (a) the diagnostic IS emitted via the logger, and (b) the original input is returned (graceful degradation preserved).
  */
final class HtmlMinifierDiagnosticsIss1028Suite extends munit.FunSuite {

  /** A JsCompressor that always throws, simulating a compression failure. */
  private object FailingJsCompressor extends JsCompressor {
    override def compress(input: String): String =
      throw new RuntimeException("simulated JS compression failure")
  }

  /** A Logger that collects all warnings into a mutable buffer for assertions. */
  final private class CollectingLogger extends ssg.minify.Logger {
    val warnings: scala.collection.mutable.ListBuffer[String] =
      scala.collection.mutable.ListBuffer.empty[String]

    override def warn(message: String): Unit =
      warnings += message
  }

  // HTML with an inline <script> block that will route through jsCompressor.compress
  // (HtmlMinifier.scala compressInlineJs filters out scripts with `src` and empty
  // content, so we need a non-empty body without a src attribute).
  private val htmlWithInlineScript =
    """<html><body><script>var x = 1;</script></body></html>"""

  // Options that enable JS compression in HTML (the default), ensuring the
  // failing compressor is actually invoked.
  private val opts = HtmlMinifyOptions(compressJsInHtml = true)

  test("ISS-1028: diagnostic emitted on compression failure (jekyll-minifier.rb:1014)") {
    val logger = new CollectingLogger
    val result = HtmlMinifier.minify(
      htmlWithInlineScript,
      opts,
      FailingJsCompressor,
      logger = logger
    )

    // (a) The diagnostic must have been emitted exactly once.
    assertEquals(
      logger.warnings.size,
      1,
      s"Expected exactly 1 warning, got ${logger.warnings.size}: ${logger.warnings.toList}"
    )

    // (b) The warning message must include the exception type and message,
    // matching the jekyll-minifier.rb:1014 style.
    val warning = logger.warnings.head
    assert(
      warning.contains("HTML compression failed"),
      s"Expected 'HTML compression failed' in warning, got: $warning"
    )
    assert(
      warning.contains("simulated JS compression failure"),
      s"Expected exception message in warning, got: $warning"
    )
    assert(
      warning.contains("RuntimeException"),
      s"Expected exception class name in warning, got: $warning"
    )

    // (c) Graceful degradation: original input returned.
    assertEquals(result, htmlWithInlineScript)
  }

  test("ISS-1028: graceful degradation still returns input on failure") {
    // Even without a logger, the input must be returned on failure (backward compat).
    val result = HtmlMinifier.minify(
      htmlWithInlineScript,
      opts,
      FailingJsCompressor
    )
    assertEquals(result, htmlWithInlineScript)
  }

  test("ISS-1028: no diagnostic emitted on successful minification") {
    val logger = new CollectingLogger
    val input  = "<html><body><p>hello   world</p></body></html>"
    val result = HtmlMinifier.minify(input, HtmlMinifyOptions.Defaults, logger = logger)

    // No warnings should be emitted on success.
    assertEquals(
      logger.warnings.size,
      0,
      s"Expected 0 warnings on success, got ${logger.warnings.size}: ${logger.warnings.toList}"
    )
    // The result should be minified (multi-spaces collapsed).
    assert(result.contains("hello world"), s"Expected minified output, got: $result")
  }

  test("ISS-1028: quiet logger (default) does not throw") {
    // Verify the default (quiet) logger does not cause any issues.
    val result = HtmlMinifier.minify(
      htmlWithInlineScript,
      opts,
      FailingJsCompressor,
      logger = ssg.minify.Logger.quiet
    )
    assertEquals(result, htmlWithInlineScript)
  }
}
