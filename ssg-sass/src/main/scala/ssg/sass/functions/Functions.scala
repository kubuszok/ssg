/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions.dart (barrel)
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: functions.dart -> Functions.scala (barrel)
 *   Convention: Phase 9 skeleton — aggregates per-category function lists.
 */
package ssg
package sass
package functions

import ssg.sass.Callable

/** Aggregator for all built-in Sass functions.
  *
  * Skeleton — see source `lib/src/functions.dart` for the global registration
  * pattern. Concrete callables will be implemented in a future phase.
  */
object Functions {

  /** All globally available built-in callables (currently empty). */
  def global: List[Callable] =
    ColorFunctions.global :::
      ListFunctions.global :::
      MapFunctions.global :::
      MathFunctions.global :::
      MetaFunctions.global :::
      SelectorFunctions.global :::
      StringFunctions.global

  /** Per-module callables, keyed by `sass:` module name. */
  def modules: Map[String, List[Callable]] = Map(
    "color" -> ColorFunctions.module,
    "list" -> ListFunctions.module,
    "map" -> MapFunctions.module,
    "math" -> MathFunctions.module,
    "meta" -> MetaFunctions.module,
    "selector" -> SelectorFunctions.module,
    "string" -> StringFunctions.module
  )
}
