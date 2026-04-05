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
 */
package ssg
package sass
package util

import scala.collection.mutable

/**
 * A mostly-unmodifiable view of a map that only allows certain keys.
 * Unmodifiable except for remove(), used for @use with configuration.
 */
final class LimitedMapView[K, V] private (
  private val map: mutable.Map[K, V],
  private val allowedKeys: mutable.Set[K]
) extends scala.collection.immutable.AbstractMap[K, V] {

  override def get(key: K): Option[V] = {
    if (allowedKeys.contains(key)) map.get(key)
    else None
  }

  override def iterator: Iterator[(K, V)] = {
    allowedKeys.iterator.flatMap { k =>
      map.get(k).map(v => (k, v))
    }
  }

  override def removed(key: K): Map[K, V] =
    iterator.toMap.removed(key)

  override def updated[V1 >: V](key: K, value: V1): Map[K, V1] =
    iterator.toMap.updated(key, value)

  override def size: Int = allowedKeys.size

  /** Removes key from the underlying map if it's in the allowed set. */
  def remove(key: K): Option[V] = {
    if (allowedKeys.contains(key)) {
      allowedKeys -= key
      map.remove(key)
    } else {
      None
    }
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
