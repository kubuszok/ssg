/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/prefixed_map_view.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: prefixed_map_view.dart → PrefixedMapView.scala
 *   Convention: Read-only map view adding prefix to keys
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/prefixed_map_view.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package util

/** An unmodifiable view of a map with string keys that presents keys with an additional prefix.
  */
final class PrefixedMapView[V](
  private val map:    Map[String, V],
  private val prefix: String
) extends scala.collection.immutable.AbstractMap[String, V] {

  override def get(key: String): Option[V] =
    if (key.startsWith(prefix)) map.get(key.substring(prefix.length))
    else None

  override def iterator: Iterator[(String, V)] =
    map.iterator.map { case (k, v) => (prefix + k, v) }

  override def removed(key: String): Map[String, V] =
    iterator.toMap.removed(key)

  override def updated[V1 >: V](key: String, value: V1): Map[String, V1] =
    iterator.toMap.updated(key, value)

  override def size: Int = map.size
}
