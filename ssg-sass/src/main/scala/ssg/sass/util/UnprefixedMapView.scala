/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/unprefixed_map_view.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: unprefixed_map_view.dart → UnprefixedMapView.scala
 *   Convention: Mostly-unmodifiable view; remove() supported for @use with
 */
package ssg
package sass
package util

import scala.collection.mutable

/** A view of a map with string keys that strips a prefix from keys. Keys without the prefix are hidden. Remove is supported for @use with.
  */
final class UnprefixedMapView[V](
  private val map:    mutable.Map[String, V],
  private val prefix: String
) extends scala.collection.immutable.AbstractMap[String, V] {

  override def get(key: String): Option[V] = map.get(prefix + key)

  override def iterator: Iterator[(String, V)] =
    map.iterator.collect {
      case (k, v) if k.startsWith(prefix) => (k.substring(prefix.length), v)
    }

  override def removed(key: String): Map[String, V] =
    iterator.toMap.removed(key)

  override def updated[V1 >: V](key: String, value: V1): Map[String, V1] =
    iterator.toMap.updated(key, value)

  override def size: Int = map.keys.count(_.startsWith(prefix))

  /** Removes key (with prefix re-added) from the underlying map. */
  def remove(key: String): Option[V] = map.remove(prefix + key)
}
