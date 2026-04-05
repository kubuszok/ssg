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
 */
package ssg
package js

/** Standalone JavaScript compressor using the full Terser engine. */
object TerserJsCompressor {

  /** Compress JavaScript source code using the Terser engine.
    *
    * @param input
    *   JavaScript source code
    * @return
    *   minified JavaScript, or original on error (graceful degradation)
    */
  def compress(input: String): String =
    if (input.trim.isEmpty) {
      input
    } else {
      try
        Terser.minifyToString(input)
      catch {
        case _: Exception => input
      }
    }
}
