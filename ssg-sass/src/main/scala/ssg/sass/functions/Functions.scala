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
 *   Convention: Phase 9 — aggregates per-category function lists.
 */
package ssg
package sass
package functions

import ssg.sass.{BuiltInCallable, Callable}

/** Aggregator for all built-in Sass functions. */
object Functions {

  /** All globally available built-in callables. */
  def global: List[Callable] =
    ColorFunctions.global :::
      ListFunctions.global :::
      MapFunctions.global :::
      MathFunctions.global :::
      MetaFunctions.global :::
      SelectorFunctions.global :::
      StringFunctions.global

  /** Looks up a global built-in by name. */
  def lookupGlobal(name: String): Option[BuiltInCallable] = {
    global.collectFirst {
      case b: BuiltInCallable if b.name == name => b
    }
  }

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
