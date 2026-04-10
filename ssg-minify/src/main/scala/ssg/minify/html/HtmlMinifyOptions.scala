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
 *   Audited: 2026-04-07 (minor_issues)
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
  preservePhp:              Boolean = false,
  preserveSsi:              Boolean = false,
  preservePatterns:         List[Regex] = Nil,
  preservedTags:            List[String] = HtmlMinifyOptions.DefaultPreservedTags
) {

  /** Effective preserve patterns, including auto-injected patterns from boolean flags. */
  def effectivePreservePatterns: List[Regex] = {
    var patterns = preservePatterns
    if (preservePhp) patterns = HtmlMinifyOptions.PhpPattern :: patterns
    if (preserveSsi) patterns = HtmlMinifyOptions.SsiPattern :: patterns
    patterns
  }
}

object HtmlMinifyOptions {
  /** Default tags whose content is preserved during minification. */
  val DefaultPreservedTags: List[String] = List("pre", "textarea", "script", "style")

  val Defaults: HtmlMinifyOptions = HtmlMinifyOptions()

  /** Regex to preserve PHP blocks: <?php ... ?> */
  val PhpPattern: Regex = """<\?php[\s\S]*?\?>""".r

  /** Regex to preserve SSI directives: <!--# ... --> */
  val SsiPattern: Regex = """<!--#[\s\S]*?-->""".r
}
