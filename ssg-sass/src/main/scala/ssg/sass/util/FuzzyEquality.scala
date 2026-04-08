/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/fuzzy_equality.dart
 * Original: Copyright (c) 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: FuzzyEquality → FuzzyEquality
 *   Convention: Dart Equality[Double] → Scala Equiv[Double]
 */
package ssg
package sass
package util

/** An Equiv implementation for doubles that uses fuzzy equality. */
object FuzzyEquality extends Equiv[Double] {
  override def equiv(x: Double, y: Double): Boolean = NumberUtil.fuzzyEquals(x, y)
}
