/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/iterable.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: IterableExtension → IterableUtil extension
 *   Convention: Dart extension → Scala 3 extension
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/iterable.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package util

import ssg.sass.Nullable
import ssg.sass.Nullable.*

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

object IterableUtil {

  extension [E](iterable: Iterable[E]) {

    /** Returns the first non-null result of callback for an element, or Nullable.Null. */
    def search[T](callback: E => Nullable[T]): Nullable[T] =
      boundary {
        val iter = iterable.iterator
        while (iter.hasNext) {
          val result = callback(iter.next())
          if (result.isDefined) break(result)
        }
        Nullable.Null
      }

    /** Returns all elements except the last. Throws if empty. */
    def exceptLast: Iterable[E] = {
      val size = iterable.size
      if (size == 0) throw new IllegalStateException("Iterable may not be empty")
      iterable.take(size - 1)
    }
  }
}
