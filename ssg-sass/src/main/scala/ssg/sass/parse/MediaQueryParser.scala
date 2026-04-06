/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/media_query.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: media_query.dart -> MediaQueryParser.scala
 *   Skeleton: parse() signature only.
 */
package ssg
package sass
package parse

import ssg.sass.{InterpolationMap, Nullable}
import ssg.sass.ast.css.CssMediaQuery

/** A parser for `@media` queries. */
class MediaQueryParser(
  contents: String,
  url: Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) extends Parser(contents, url, interpolationMap) {

  def parse(): List[CssMediaQuery] =
    throw new UnsupportedOperationException("MediaQueryParser.parse: not yet implemented in skeleton")
}
