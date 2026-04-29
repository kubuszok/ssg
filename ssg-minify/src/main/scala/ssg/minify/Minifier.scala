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
 *
 * Covenant: full-port
 * Covenant-ruby-reference: lib/jekyll-minifier.rb
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 5422b3570321668b419ec8271391a029f385c390
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

  /** Minify content based on file type.
    *
    * Respects file-type toggles (compressCss, compressJs, compressJson) — returns input unchanged if the toggle is off.
    */
  def minify(
    input:        String,
    fileType:     FileType,
    options:      MinifyOptions = MinifyOptions.Defaults,
    jsCompressor: JsCompressor = BasicJsMinifier
  ): String =
    fileType match {
      case FileType.Html => HtmlMinifier.minify(input, options.html, jsCompressor)
      case FileType.Xml  => HtmlMinifier.minify(input, options.html, jsCompressor)
      case FileType.Css  => if (options.compressCss) CssMinifier.minify(input, options.css) else input
      case FileType.Js   => if (options.compressJs) options.jsCompressorOpts.fold(jsCompressor.compress(input))(opts => jsCompressor.compress(input, opts)) else input
      case FileType.Json => if (options.compressJson) JsonMinifier.minify(input) else input
    }

  /** Minify content based on file path, respecting exclude patterns and file-type toggles. */
  def minifyFile(
    input:        String,
    filePath:     String,
    options:      MinifyOptions = MinifyOptions.Defaults,
    jsCompressor: JsCompressor = BasicJsMinifier
  ): String =
    if (options.exclude.exists(pattern => filePath.contains(pattern))) input
    else {
      fileTypeFromPath(filePath) match {
        case Some(ft) => minify(input, ft, options, jsCompressor)
        case None     => input
      }
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
