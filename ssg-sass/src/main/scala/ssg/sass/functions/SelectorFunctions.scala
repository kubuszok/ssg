/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/selector.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: selector.dart -> SelectorFunctions.scala
 *   Convention: Phase 9 skeleton — registration entry points only.
 */
package ssg
package sass
package functions

import ssg.sass.Callable
import ssg.sass.value.Value

/** Built-in selector functions: nest, append, extend, replace, unify,
  * is-superselector, simple-selectors, parse, etc.
  *
  * Skeleton — see source `lib/src/functions/selector.dart`.
  */
object SelectorFunctions {

  def global: List[Callable] = Nil
  def module: List[Callable] = Nil

  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Phase 9 stub: selector." + name)
}
