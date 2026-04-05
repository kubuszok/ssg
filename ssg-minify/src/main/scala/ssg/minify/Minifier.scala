/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Top-level minification facade — dispatches to type-specific minifiers.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: Jekyll::Compressor → ssg.minify.Minifier
 *   Convention: Stateless facade, pure functions
 *   Idiom: Delegates to HtmlMinifier, CssMinifier, JsMinifier, JsonMinifier
 */
package ssg
package minify

import ssg.minify.css.CssMinifier
import ssg.minify.js.JsMinifier as BasicJsMinifier
import ssg.minify.json.JsonMinifier
import ssg.minify.html.HtmlMinifier

/** Top-level minification facade.
  *
  * By default uses the basic JS minifier (comment/whitespace removal). For full AST-based JS minification, pass `ssg.js.TerserJsCompressor` as the `jsCompressor` parameter.
  */
object Minifier {

  /** Minify HTML content. */
  def minifyHtml(input: String, options: MinifyOptions = MinifyOptions.Defaults): String =
    HtmlMinifier.minify(input, options.html)

  /** Minify HTML content with a custom JS compressor. */
  def minifyHtml(input: String, options: MinifyOptions, jsCompressor: JsCompressor): String =
    HtmlMinifier.minify(input, options.html, jsCompressor)

  /** Minify CSS content. */
  def minifyCss(input: String, options: MinifyOptions = MinifyOptions.Defaults): String =
    CssMinifier.minify(input, options.css)

  /** Minify JavaScript content (basic: comment/whitespace removal). For full minification use ssg.js.TerserJsCompressor. */
  def minifyJs(input: String, options: MinifyOptions = MinifyOptions.Defaults): String =
    BasicJsMinifier.minify(input, options.js)

  /** Minify JSON content. */
  def minifyJson(input: String): String =
    JsonMinifier.minify(input)

  /** Minify content based on file type. */
  def minify(
    input:        String,
    fileType:     FileType,
    options:      MinifyOptions = MinifyOptions.Defaults,
    jsCompressor: JsCompressor = BasicJsMinifier
  ): String =
    fileType match {
      case FileType.Html => HtmlMinifier.minify(input, options.html, jsCompressor)
      case FileType.Xml  => HtmlMinifier.minify(input, options.html, jsCompressor)
      case FileType.Css  => CssMinifier.minify(input, options.css)
      case FileType.Js   => jsCompressor.compress(input)
      case FileType.Json => JsonMinifier.minify(input)
    }

  /** Determine file type from a file path extension. */
  def fileTypeFromPath(path: String): Option[FileType] = {
    val dot = path.lastIndexOf('.')
    if (dot < 0) {
      None
    } else {
      path.substring(dot + 1).toLowerCase match {
        case "html" | "htm" => Some(FileType.Html)
        case "xml"          => Some(FileType.Xml)
        case "css"          => Some(FileType.Css)
        case "js"           => Some(FileType.Js)
        case "json"         => Some(FileType.Json)
        case _              => None
      }
    }
  }
}
