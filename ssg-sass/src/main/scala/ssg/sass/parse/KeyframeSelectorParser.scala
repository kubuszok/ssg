/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/keyframe_selector.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: keyframe_selector.dart -> KeyframeSelectorParser.scala
 *   Skeleton: parse() signature only.
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationMap, Nullable }

/** A parser for `@keyframes` block selectors. */
class KeyframeSelectorParser(
  contents:         String,
  url:              Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) extends Parser(contents, url, interpolationMap) {

  def parse(): List[String] =
    throw new UnsupportedOperationException("KeyframeSelectorParser.parse: not yet implemented in skeleton")
}
