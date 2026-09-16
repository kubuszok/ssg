/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Standalone JavaScript compressor using the Terser engine.
 *
 * To use with ssg-minify's JsCompressor trait, create an adapter in
 * your application:
 * {{{
 *   object MyJsCompressor extends ssg.minify.JsCompressor {
 *     def compress(input: String): String = TerserJsCompressor.compress(input)
 *   }
 *   HtmlMinifier.minify(html, jsCompressor = MyJsCompressor)
 * }}}
 *
 * Or use the pre-built adapter in the ssg aggregator module.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package js

import ssg.commons.{ DiagResult, Diagnostic }

/** Standalone JavaScript compressor using the full Terser engine. */
object TerserJsCompressor {

  /** Compress JavaScript source code using the Terser engine.
    *
    * On failure the original input is returned (graceful degradation). The diagnostic is surfaced via the injectable `logger` channel so callers can observe failures — mirrors the ISS-1028 pattern in
    * ssg-minify's HtmlMinifier (jekyll-minifier.rb:1013-1015).
    *
    * @param input
    *   JavaScript source code
    * @param logger
    *   diagnostics channel (defaults to quiet — no output)
    * @return
    *   minified JavaScript, or original on error (graceful degradation)
    */
  def compress(input: String, logger: ssg.commons.Logger = ssg.commons.Logger.quiet): String =
    if (input.trim.isEmpty) {
      input
    } else {
      try
        Terser.minifyToString(input)
      catch {
        case e: Exception =>
          logger.warn(s"JS compression failed: ${e.getClass.getName}: ${e.getMessage}. Using original source.")
          input
      }
    }

  /** Compress JavaScript source code, returning a diagnostics envelope (ISS-1377).
    *
    * Additive facade over [[compress]] per docs/architecture/error-contracts.md section 2.5, built with the SAME collecting-logger technique as ssg-minify's `HtmlMinifier.minifyResult`: a private
    * buffering `ssg.commons.Logger` is passed into the existing `compress`, and each warned message is mapped afterwards into a `Severity.Warning` [[ssg.commons.Diagnostic]] with component `"ssg-js"`
    * and code `"js-compression-failed"`. No new catch is introduced — the ISS-1052 compress + catch + Logger channel stays intact.
    *
    * The return-input-unchanged degradation (graceful passthrough on a compression failure) is a Warning + success per the section 1.1 severity policy: the passthrough content is still correct JS,
    * merely uncompressed, so `isSuccess` stays true. A clean compress carries no diagnostics and the compressed string; the value is byte-identical to what [[compress]] returns for the same input.
    *
    * @param input
    *   JavaScript source code
    * @return
    *   a success carrying the compressed JS (or the original on passthrough), plus one Warning diagnostic per compression failure
    */
  def compressResult(input: String): DiagResult[String] = {
    // A private collector Logger appends each warned message to a local buffer; the buffer is mapped to
    // Warning diagnostics afterwards, so no new catch is introduced (docs/architecture/error-contracts.md 2.5).
    val buffer = scala.collection.mutable.ListBuffer.empty[String]
    val collector: ssg.commons.Logger = new ssg.commons.Logger {
      override def warn(message: String): Unit =
        buffer += message
    }
    val output = compress(input, collector)
    DiagResult(Some(output), buffer.toVector.map(msg => Diagnostic.warning("ssg-js", msg, code = Some("js-compression-failed"))))
  }
}
