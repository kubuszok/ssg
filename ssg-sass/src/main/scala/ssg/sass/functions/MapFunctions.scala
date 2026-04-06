/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/map.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: map.dart -> MapFunctions.scala
 *   Convention: Phase 9 skeleton — registration entry points only.
 */
package ssg
package sass
package functions

import ssg.sass.Callable
import ssg.sass.value.Value

/** Built-in map functions: get, set, merge, remove, keys, values, has-key,
  * deep-merge, deep-remove, etc.
  *
  * Skeleton — see source `lib/src/functions/map.dart`.
  */
object MapFunctions {

  def global: List[Callable] = Nil
  def module: List[Callable] = Nil

  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Phase 9 stub: map." + name)
}
