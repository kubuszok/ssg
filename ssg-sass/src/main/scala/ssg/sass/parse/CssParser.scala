/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/css.dart
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: css.dart -> CssParser.scala
 *   Skeleton: extends ScssParser; `plainCss` returns true so statements that
 *     are disallowed in plain CSS can reject them in a later pass.
 */
package ssg
package sass
package parse

import ssg.sass.Nullable

/** A parser for plain CSS. */
class CssParser(
  contents: String,
  url: Nullable[String] = Nullable.Null,
  parseSelectors: Boolean = false
) extends ScssParser(contents, url, parseSelectors) {

  override def plainCss: Boolean = true
}
