/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/merged_map_view.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: merged_map_view.dart → MergedMapView.scala
 *   Convention: Dart MapBase → Scala AbstractMap
 *   Idiom: Flattens nested MergedMapViews for O(1) access
 */
package ssg
package sass
package util

import scala.collection.mutable

/**
 * An unmodifiable view of multiple maps merged together.
 * Values in later maps take precedence. Setting a key updates the map that owns it.
 * The underlying maps' key sets must remain unchanged.
 */
final class MergedMapView[K, V](maps: Iterable[mutable.Map[K, V]]) extends mutable.AbstractMap[K, V] {

  /** Maps each key to the map that contains it. */
  private val mapsByKey: mutable.Map[K, mutable.Map[K, V]] = mutable.LinkedHashMap.empty

  // Initialize: flatten nested MergedMapViews
  for (map <- maps) {
    map match {
      case merged: MergedMapView[K @unchecked, V @unchecked] =>
        for ((_, child) <- merged.mapsByKey.toSeq.distinctBy(_._2)) {
          MapUtil.setAll(mapsByKey, child.keys, child)
        }
      case _ =>
        MapUtil.setAll(mapsByKey, map.keys, map)
    }
  }

  override def get(key: K): Option[V] = {
    mapsByKey.get(key).flatMap(_.get(key))
  }

  override def iterator: Iterator[(K, V)] = {
    mapsByKey.keysIterator.flatMap { k =>
      mapsByKey.get(k).flatMap(_.get(k)).map(v => (k, v))
    }
  }

  override def size: Int = mapsByKey.size

  override def subtractOne(key: K): this.type =
    throw new UnsupportedOperationException("Entries may not be removed from MergedMapView.")

  override def addOne(elem: (K, V)): this.type = {
    val (key, value) = elem
    mapsByKey.get(key) match {
      case Some(child) =>
        child(key) = value
        this
      case None =>
        throw new UnsupportedOperationException("New entries may not be added to MergedMapView.")
    }
  }

  override def update(key: K, value: V): Unit = {
    mapsByKey.get(key) match {
      case Some(child) => child(key) = value
      case None =>
        throw new UnsupportedOperationException("New entries may not be added to MergedMapView.")
    }
  }

  override def contains(key: K): Boolean = mapsByKey.contains(key)
}
