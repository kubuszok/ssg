/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapter that bridges ssg-js (Terser engine) into ssg-minify's
 * pluggable JsCompressor system.
 *
 * Usage:
 * {{{
 *   import ssg.TerserJsCompressorAdapter
 *   import ssg.minify.html.HtmlMinifier
 *
 *   val html = HtmlMinifier.minify(input, jsCompressor = TerserJsCompressorAdapter)
 *   val js = ssg.minify.Minifier.minify(code, ssg.minify.FileType.Js,
 *              jsCompressor = TerserJsCompressorAdapter)
 * }}}
 */
package ssg

/** JsCompressor adapter using the full Terser engine from ssg-js. */
object TerserJsCompressorAdapter extends ssg.minify.JsCompressor {
  override def compress(input: String): String =
    ssg.js.TerserJsCompressor.compress(input)

  /** Compress with options, mirroring `::Terser.new(config.terser_args).compile` (jekyll-minifier.rb:384/435). A [[TerserJsCompressorOptions]] is mapped to ssg-js Terser's `MinifyOptions` and run
    * through the engine with the same graceful-degradation semantics as the 1-arg path (jekyll-minifier.rb:1013-1015 `rescue => e`): on failure the original input is returned. Any other
    * `JsCompressorOptions` subtype falls back to the 1-arg default path.
    */
  override def compress(input: String, options: ssg.minify.JsCompressorOptions): String =
    compress(input, options, ssg.commons.Logger.quiet)

  /** Options-aware compress with an explicit diagnostics channel — mirrors the ISS-1028 pattern in [[ssg.js.TerserJsCompressor.compress]]: on failure the original input is returned (graceful
    * degradation, jekyll-minifier.rb:1013-1015) and the diagnostic is surfaced via `logger` rather than swallowed.
    */
  def compress(input: String, options: ssg.minify.JsCompressorOptions, logger: ssg.commons.Logger): String =
    options match {
      case opts: TerserJsCompressorOptions =>
        if (input.trim.isEmpty) {
          input
        } else {
          try
            ssg.js.Terser.minifyToString(input, opts.toMinifyOptions)
          catch {
            case e: Exception =>
              logger.warn(s"JS compression failed: ${e.getClass.getName}: ${e.getMessage}. Using original source.")
              input
          }
        }
      case _ => compress(input)
    }
}
