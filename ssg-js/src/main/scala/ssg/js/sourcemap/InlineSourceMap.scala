/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Inline source-map reader — ports terser's `read_source_map` (lib/minify.js:33-40).
 *
 * Extracts the trailing `//# sourceMappingURL=data:application/json[;...];base64,<B64>`
 * comment from a piece of JS source, Base64-decodes it, and returns the embedded
 * source-map JSON string. Used when `options.sourceMap.content == "inline"`
 * (lib/minify.js:226-230).
 *
 * Original source: terser lib/minify.js:33-40 read_source_map
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: read_source_map -> readSourceMap.
 *   Idiom: the upstream inline-map regex is ported verbatim; the absent-map
 *     warning (console.warn) maps to System.err.println, preserving the
 *     null result.
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/minify.js:33-40 read_source_map
 * Covenant-verified: 2026-06-20
 */
package ssg
package js
package sourcemap

import scala.util.matching.Regex

/** Reads an inline source map embedded as a trailing data-URI comment. */
object InlineSourceMap {

  // terser/minify.js:34 — the exact upstream regex:
  //   /(?:^|[^.])\/\/# sourceMappingURL=data:application\/json(;[\w=-]*)?;base64,([+/0-9A-Za-z]*=*)\s*$/
  // (?:^|[^.]) ensures the comment is not part of a string/URL containing a dot;
  // (;[\w=-]*)? optionally matches the `;charset=utf-8` segment; group 2 is the Base64.
  private val InlineMapRegex: Regex =
    new Regex("(?:^|[^.])//# sourceMappingURL=data:application/json(;[\\w=-]*)?;base64,([+/0-9A-Za-z]*=*)\\s*$")

  /** Port of `read_source_map(code)` (minify.js:33-40): return the decoded inline
    * source-map JSON string, or `null` when no inline map is present (upstream
    * logs `console.warn("inline source map not found")` and returns `null`).
    */
  def readSourceMap(code: String): String | Null =
    InlineMapRegex.findFirstMatchIn(code) match {
      case Some(m) => Base64.decode(m.group(2))
      case None =>
        // minify.js:36 — console.warn("inline source map not found")
        System.err.println("inline source map not found")
        null
    }
}
