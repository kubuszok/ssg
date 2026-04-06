/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/at_root_query.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: at_root_query.dart -> AtRootQueryParser.scala
 *   Skeleton: parse() signature only.
 */
package ssg
package sass
package parse

import ssg.sass.{InterpolationMap, Nullable}
import ssg.sass.ast.sass.AtRootQuery

/** A parser for `@at-root` queries. */
class AtRootQueryParser(
  contents: String,
  url: Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) extends Parser(contents, url, interpolationMap) {

  def parse(): AtRootQuery =
    throw new UnsupportedOperationException("AtRootQueryParser.parse: not yet implemented in skeleton")
}
