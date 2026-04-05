/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * HTML minification options.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb (htmlcompressor gem)
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: htmlcompressor gem → ssg.minify.html.HtmlMinifier
 *   Convention: Immutable case class matching all original config options
 */
package ssg
package minify
package html

import scala.util.matching.Regex

final case class HtmlMinifyOptions(
  removeComments:           Boolean = true,
  removeIntertagSpaces:     Boolean = false,
  removeMultiSpaces:        Boolean = true,
  removeSpacesInsideTags:   Boolean = true,
  removeQuotes:             Boolean = false,
  simpleDoctype:            Boolean = false,
  removeScriptAttributes:   Boolean = false,
  removeStyleAttributes:    Boolean = false,
  removeLinkAttributes:     Boolean = false,
  removeFormAttributes:     Boolean = false,
  removeInputAttributes:    Boolean = false,
  removeJavascriptProtocol: Boolean = false,
  removeHttpProtocol:       Boolean = false,
  removeHttpsProtocol:      Boolean = false,
  preserveLineBreaks:       Boolean = false,
  simpleBooleanAttributes:  Boolean = false,
  compressCssInHtml:        Boolean = true,
  compressJsInHtml:         Boolean = true,
  preservePatterns:         List[Regex] = Nil
)

object HtmlMinifyOptions {
  val Defaults: HtmlMinifyOptions = HtmlMinifyOptions()
}
