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
}
