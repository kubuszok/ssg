/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/meta.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: meta.dart -> MetaFunctions.scala
 *   Convention: Phase 9 skeleton — registration entry points only.
 */
package ssg
package sass
package functions

import ssg.sass.Callable
import ssg.sass.value.Value

/** Built-in meta functions: type-of, inspect, keywords, feature-exists,
  * variable-exists, global-variable-exists, function-exists, mixin-exists,
  * content-exists, get-function, call, calc-name, calc-args, etc.
  *
  * Skeleton — see source `lib/src/functions/meta.dart`.
  */
object MetaFunctions {

  def global: List[Callable] = Nil
  def module: List[Callable] = Nil

  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Phase 9 stub: meta." + name)
}
