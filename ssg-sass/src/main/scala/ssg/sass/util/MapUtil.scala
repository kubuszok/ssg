/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/map.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: MapExtensions → MapUtil extension
 *   Convention: Dart Option typedef → Scala Option
 *   Idiom: getOption is redundant with Scala's Map.get; kept for API compatibility
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/map.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package util

import scala.collection.mutable

object MapUtil {

  extension [K, V](map: mutable.Map[K, V]) {

    /** If map doesn't contain key, sets it to value and returns it. Otherwise, merges with existing. */
    def putOrMerge(key: K, value: V, merge: (V, V) => V): V =
      map.get(key) match {
        case Some(existing) =>
          val merged = merge(existing, value)
          map(key) = merged
          merged
        case None =>
          map(key) = value
          value
      }
  }

  /** Sets all keys in map to value. */
  def setAll[K, V](map: mutable.Map[K, V], keys: Iterable[K], value: V): Unit =
    for (key <- keys)
      map(key) = value
}
