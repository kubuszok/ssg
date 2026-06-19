/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Permalink styles for the site pipeline.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 6 for design.
 *
 * Permalink resolution logic: ISS-1209
 */
package ssg
package site

/** Jekyll-compatible built-in permalink styles.
  *
  * The three styles match Jekyll's built-in permalink presets. Full permalink RESOLUTION (expanding placeholders like `:categories`, `:year`, `:title`) is handled in ISS-1209; this enum defines only
  * the style identifiers and their template strings so that `SiteConfig` can reference them.
  */
enum PermalinkStyle(val template: String) extends java.lang.Enum[PermalinkStyle] {

  /** Jekyll `date` style: `/:categories/:year/:month/:day/:title:output_ext`.
    *
    * For regular pages (non-collection), the category/date placeholders are empty, so this resolves to the source-relative path (design section 6).
    */
  case Date extends PermalinkStyle("/:categories/:year/:month/:day/:title:output_ext")

  /** Jekyll `pretty` style: `/:categories/:year/:month/:day/:title/`. */
  case Pretty extends PermalinkStyle("/:categories/:year/:month/:day/:title/")

  /** Jekyll `none` style: `/:path:output_ext`. */
  case None extends PermalinkStyle("/:path:output_ext")
}
