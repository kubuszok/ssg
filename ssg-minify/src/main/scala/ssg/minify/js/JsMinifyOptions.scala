/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JavaScript minification options.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb (terser gem)
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: terser gem → ssg.minify.js.JsMinifier (basic implementation)
 *   Convention: Immutable case class with sensible defaults
 *   Idiom: Full Terser port planned as separate ssg-js module
 */
package ssg
package minify
package js

final case class JsMinifyOptions(
  removeComments:     Boolean = true,
  collapseWhitespace: Boolean = true
)

object JsMinifyOptions {
  val Defaults: JsMinifyOptions = JsMinifyOptions()
}
