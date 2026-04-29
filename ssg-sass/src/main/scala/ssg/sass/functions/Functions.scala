/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: functions.dart -> Functions.scala (barrel)
 *   Convention: Phase 9 — aggregates per-category function lists.
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/functions.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package functions

import ssg.sass.{ BuiltInCallable, Callable }
import ssg.sass.value.Value

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
  def lookupGlobal(name: String): Option[BuiltInCallable] =
    global.collectFirst {
      case b: BuiltInCallable if b.name == name => b
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

  /** Per-module variables, keyed by `sass:` module name. Currently only `sass:math` exposes any (`$pi`, `$e`, `$epsilon`, etc.); the other built-in modules return empty maps.
    */
  def moduleVariables(moduleName: String): Map[String, Value] = moduleName match {
    case "math" => MathFunctions.moduleVariables
    case _      => Map.empty
  }
}
