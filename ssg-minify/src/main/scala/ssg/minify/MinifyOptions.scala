/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Aggregate minification options for all file types.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: CompressionConfig → ssg.minify.MinifyOptions
 *   Convention: Immutable aggregate case class
 *
 * Covenant: full-port
 * Covenant-ruby-reference: jekyll-minifier lib/jekyll-minifier.rb
 * Covenant-verified: 2026-04-26
 */
package ssg
package minify

import ssg.minify.css.CssMinifyOptions
import ssg.minify.js.JsMinifyOptions
import ssg.minify.html.HtmlMinifyOptions

final case class MinifyOptions(
  html:             HtmlMinifyOptions = HtmlMinifyOptions.Defaults,
  css:              CssMinifyOptions = CssMinifyOptions.Defaults,
  js:               JsMinifyOptions = JsMinifyOptions.Defaults,
  jsCompressorOpts: Option[JsCompressorOptions] = None,
  compressCss:      Boolean = true,
  compressJs:       Boolean = true,
  compressJson:     Boolean = true,
  exclude:          List[String] = Nil
)

object MinifyOptions {
  val Defaults: MinifyOptions = MinifyOptions()
}
