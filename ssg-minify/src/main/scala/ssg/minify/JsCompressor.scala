/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Pluggable JavaScript compression trait.
 *
 * The default implementation is TerserJsCompressor (ssg.js) which provides
 * full AST-based JavaScript minification via the Terser engine.
 * A basic fallback (ssg.minify.js.JsMinifier) is also available for
 * comment/whitespace removal without AST parsing.
 */
package ssg
package minify

/** Pluggable JavaScript compressor. Implement this trait to provide custom JS compression (e.g., a full Terser port in ssg-js).
  */
trait JsCompressor {

  /** Compress JavaScript source code.
    *
    * @param input
    *   JavaScript source
    * @return
    *   compressed JavaScript
    */
  def compress(input: String): String
}
