/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * CSS minification options.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb (cssminify2 gem)
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: cssminify2 gem → ssg.minify.css.CssMinifier
 *   Convention: Immutable case class with sensible defaults
 *
 * Covenant: full-port
 * Covenant-ruby-reference: jekyll-minifier lib/jekyll-minifier.rb (cssminify2 gem)
 * Covenant-verified: 2026-04-26
 */
package ssg
package minify
package css

final case class CssMinifyOptions(
  removeComments:           Boolean = true,
  collapseWhitespace:       Boolean = true,
  removeEmptyRules:         Boolean = true,
  shortenColors:            Boolean = true,
  removeTrailingSemicolons: Boolean = true,
  collapseZeros:            Boolean = true
)

object CssMinifyOptions {
  val Defaults: CssMinifyOptions = CssMinifyOptions()
}
