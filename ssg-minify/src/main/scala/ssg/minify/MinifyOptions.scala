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
 * Covenant-ruby-reference: lib/jekyll-minifier.rb
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 5422b3570321668b419ec8271391a029f385c390
 */
package ssg
package minify

import ssg.minify.css.CssEnhancedConfig
import ssg.minify.css.CssMinifyOptions
import ssg.minify.html.HtmlMinifyOptions
import ssg.minify.js.JsMinifyOptions

final case class MinifyOptions(
  html:             HtmlMinifyOptions = HtmlMinifyOptions.Defaults,
  css:              CssMinifyOptions = CssMinifyOptions.Defaults,
  js:               JsMinifyOptions = JsMinifyOptions.Defaults,
  jsCompressorOpts: Option[JsCompressorOptions] = None,
  compressCss:      Boolean = true,
  compressJs:       Boolean = true,
  compressJson:     Boolean = true,
  exclude:          List[String] = Nil,
  // Enhanced CSS Compression Options (cssminify2 v2.1.0+), mirroring
  // jekyll-minifier CompressionConfig (jekyll-minifier.rb:539-545, 616-639).
  // Defaults match the Ruby get_boolean defaults: enhanced mode off,
  // preserve_ie_hacks on, the rest off.
  cssEnhancedMode:                Boolean = false,
  cssMergeDuplicateSelectors:     Boolean = false,
  cssOptimizeShorthandProperties: Boolean = false,
  cssAdvancedColorOptimization:   Boolean = false,
  cssPreserveIeHacks:             Boolean = true,
  cssCompressVariables:           Boolean = false
) {

  // Generate enhanced CSS compression options, mirroring CompressionConfig#css_enhanced_options
  // (jekyll-minifier.rb:629-639): returns None unless css_enhanced_mode?, otherwise a config
  // carrying the five enhancement flags. Ruby returns a Hash; SSG builds the equivalent
  // CssEnhancedConfig that CssEnhancedCompressor consumes (CSSEnhancedWrapper, jekyll-minifier.rb:336-337).
  def cssEnhancedOptions: Option[CssEnhancedConfig] =
    if (!cssEnhancedMode) {
      None
    } else {
      val config = new CssEnhancedConfig()
      config.mergeDuplicateSelectors = cssMergeDuplicateSelectors
      config.optimizeShorthandProperties = cssOptimizeShorthandProperties
      config.advancedColorOptimization = cssAdvancedColorOptimization
      config.preserveIeHacks = cssPreserveIeHacks
      config.compressCssVariables = cssCompressVariables
      Some(config)
    }
}

object MinifyOptions {
  val Defaults: MinifyOptions = MinifyOptions()
}
