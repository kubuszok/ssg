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
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/unprefixed_map_view.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package util

import scala.collection.mutable

/** A view of a map with string keys that strips a prefix from keys. Keys without the prefix are hidden. Supports `remove()` and `subtractOne()` so configurations can mark variables as used, but
  * `addOne()` is unsupported.
  *
  * Extends `mutable.Map` so it can be assigned to the same variable as the underlying mutable map in `Configuration.throughForward`.
  */
final class UnprefixedMapView[V](
  private val map:    mutable.Map[String, V],
  private val prefix: String
) extends mutable.AbstractMap[String, V] {

  override def get(key: String): Option[V] = map.get(prefix + key)

  override def iterator: Iterator[(String, V)] =
    map.iterator.collect {
      case (k, v) if k.startsWith(prefix) => (k.substring(prefix.length), v)
    }

  override def subtractOne(key: String): this.type = {
    map.remove(prefix + key)
    this
  }

  override def addOne(elem: (String, V)): this.type =
    throw new UnsupportedOperationException("UnprefixedMapView does not support addOne")

  override def size: Int = map.keys.count(_.startsWith(prefix))

  /** Removes key (with prefix re-added) from the underlying map. */
  override def remove(key: String): Option[V] = map.remove(prefix + key)
}
