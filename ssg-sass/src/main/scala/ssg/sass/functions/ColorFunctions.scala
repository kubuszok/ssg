/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/color.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: color.dart -> ColorFunctions.scala
 *   Convention: Phase 9 skeleton — registration entry points only.
 *   Idiom: Each Sass-side function will be registered against an evaluator
 *          environment in a future phase. The body is currently a stub that
 *          throws UnsupportedOperationException("Phase 9 stub").
 */
package ssg
package sass
package functions

import ssg.sass.Callable
import ssg.sass.value.Value

/** Built-in color functions: rgb, rgba, hsl, hwb, lab, lch, oklab, oklch,
  * color, mix, adjust, change, scale, complement, invert, etc.
  *
  * Skeleton — see source `lib/src/functions/color.dart` for the full list
  * (~1500 lines). Will be ported alongside the evaluator.
  */
object ColorFunctions {

  /** Stub: return the (currently empty) list of built-in color callables. */
  def global: List[Callable] = Nil

  /** Stub: return the (currently empty) list of `sass:color` module callables. */
  def module: List[Callable] = Nil

  /** Stub for any direct color function dispatch. */
  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Phase 9 stub: color." + name)
}
