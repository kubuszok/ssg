/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/math.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: math.dart -> MathFunctions.scala
 *   Convention: Phase 9 skeleton — registration entry points only.
 */
package ssg
package sass
package functions

import ssg.sass.Callable
import ssg.sass.value.Value

/** Built-in math functions: abs, ceil, floor, round, max, min, percentage,
  * random, unit, unitless, comparable, hypot, log, pow, sqrt, sin, cos, tan,
  * asin, acos, atan, atan2, etc.
  *
  * Skeleton — see source `lib/src/functions/math.dart`.
  */
object MathFunctions {

  def global: List[Callable] = Nil
  def module: List[Callable] = Nil

  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Phase 9 stub: math." + name)
}
