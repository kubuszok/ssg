/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/list.dart (ListSeparator enum)
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: ListSeparator from list.dart → ListSeparator.scala
 *   Convention: Scala 3 enum
 */
package ssg
package sass
package value

import ssg.sass.Nullable
import ssg.sass.Nullable.*

import scala.language.implicitConversions

/** The separator between elements in a Sass list. */
enum ListSeparator(val separator: String, val separatorChar: Nullable[String]) extends java.lang.Enum[ListSeparator] {
  case Space extends ListSeparator("space", " ")
  case Comma extends ListSeparator("comma", ",")
  case Slash extends ListSeparator("slash", "/")
  case Undecided extends ListSeparator("undecided", Nullable.Null)

  override def toString: String = separator
}
