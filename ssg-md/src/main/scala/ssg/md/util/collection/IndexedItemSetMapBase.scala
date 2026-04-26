/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/IndexedItemSetMapBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/IndexedItemSetMapBase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package collection

abstract class IndexedItemSetMapBase[K, S, M](capacity: Int) extends IndexedItemSetMap[K, S, M] {

  def this() = this(0)

  protected val bag: java.util.HashMap[K, S] = new java.util.HashMap[K, S]()

  override def addItem(key: M, item: Int): Boolean = {
    val mk      = mapKey(key)
    var itemSet = bag.get(mk)
    if (itemSet == null) { // Java HashMap interop — get returns null for missing keys
      itemSet = newSet()
      bag.put(mk, itemSet)
    }
    addSetItem(itemSet, item)
  }

  override def removeItem(key: M, item: Int): Boolean = {
    val mk      = mapKey(key)
    val itemSet = bag.get(mk)
    itemSet != null && removeSetItem(itemSet, item) // Java HashMap interop — get returns null for missing keys
  }

  override def containsItem(key: M, item: Int): Boolean = {
    val mk      = mapKey(key)
    val itemSet = bag.get(mk)
    itemSet != null && containsSetItem(itemSet, item) // Java HashMap interop — get returns null for missing keys
  }

  override def size(): Int = bag.size()

  override def isEmpty: Boolean = bag.isEmpty

  override def containsKey(o: Any): Boolean = bag.containsKey(o)

  override def containsValue(o: Any): Boolean = bag.containsValue(o)

  override def get(o: Any): S = bag.get(o)

  override def put(k: K, vs: S): S = bag.put(k, vs)

  override def remove(o: Any): S = bag.remove(o)

  override def putAll(map: java.util.Map[? <: K, ? <: S]): Unit = bag.putAll(map)

  override def clear(): Unit = bag.clear()

  override def keySet(): java.util.Set[K] = bag.keySet()

  override def values(): java.util.Collection[S] = bag.values()

  override def entrySet(): java.util.Set[java.util.Map.Entry[K, S]] = bag.entrySet()
}
