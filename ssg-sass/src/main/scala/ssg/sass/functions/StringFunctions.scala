/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/string.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: string.dart -> StringFunctions.scala
 *   Convention: Phase 9 skeleton — registration entry points only.
 */
package ssg
package sass
package functions

import ssg.sass.Callable
import ssg.sass.value.Value

/** Built-in string functions: unquote, quote, to-upper-case, to-lower-case,
  * length, str-length, str-insert, str-index, str-slice, unique-id, etc.
  *
  * Skeleton — see source `lib/src/functions/string.dart`.
  */
object StringFunctions {

  def global: List[Callable] = Nil
  def module: List[Callable] = Nil

  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Phase 9 stub: string." + name)
}
