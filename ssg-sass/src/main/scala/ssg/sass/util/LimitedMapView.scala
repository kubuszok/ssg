/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/limited_map_view.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: limited_map_view.dart → LimitedMapView.scala
 *   Convention: Dart UnmodifiableMapBase → Scala custom immutable Map
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/limited_map_view.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package util

import scala.collection.mutable

/** A mostly-unmodifiable view of a map that only allows certain keys. Supports `remove()` and `subtractOne()` so configurations can mark variables as used, but `addOne()` is unsupported.
  *
  * Extends `mutable.Map` so it can be assigned to the same variable as the underlying mutable map in `Configuration.throughForward`.
  */
final class LimitedMapView[K, V] private (
  private val map:         mutable.Map[K, V],
  private val allowedKeys: mutable.Set[K]
) extends mutable.AbstractMap[K, V] {

  override def get(key: K): Option[V] =
    if (allowedKeys.contains(key)) map.get(key)
    else None

  override def iterator: Iterator[(K, V)] =
    allowedKeys.iterator.flatMap { k =>
      map.get(k).map(v => (k, v))
    }

  override def subtractOne(key: K): this.type = {
    if (allowedKeys.contains(key)) {
      allowedKeys -= key
      map.remove(key)
    }
    this
  }

  override def addOne(elem: (K, V)): this.type =
    throw new UnsupportedOperationException("LimitedMapView does not support addOne")

  override def size: Int = allowedKeys.count(map.contains)

  /** Removes key from the underlying map if it's in the allowed set. */
  override def remove(key: K): Option[V] =
    if (allowedKeys.contains(key)) {
      allowedKeys -= key
      map.remove(key)
    } else {
      None
    }
}

object LimitedMapView {

  /** Creates a view that allows only keys in safelist. */
  def safelist[K, V](map: mutable.Map[K, V], safelist: Set[K]): LimitedMapView[K, V] = {
    val keys = mutable.Set.from(safelist.intersect(map.keySet.toSet))
    new LimitedMapView(map, keys)
  }

  /** Creates a view that blocks keys in blocklist. */
  def blocklist[K, V](map: mutable.Map[K, V], blocklist: Set[K]): LimitedMapView[K, V] = {
    val keys = mutable.Set.from(map.keys.filterNot(blocklist.contains))
    new LimitedMapView(map, keys)
  }
}
