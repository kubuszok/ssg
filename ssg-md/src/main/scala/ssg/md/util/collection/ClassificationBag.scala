/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/ClassificationBag.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/ClassificationBag.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable
import ssg.md.util.collection.iteration.*

import java.util.BitSet

import scala.language.implicitConversions

class ClassificationBag[K, V](
  capacity:         Int,
  mapper:           V => K,
  private val host: Nullable[CollectionHost[V]]
) {

  def this(mapper: V => K) = this(0, mapper, Nullable.empty)

  def this(mapper: V => K, host: CollectionHost[V]) = this(0, mapper, Nullable(host))

  def this(capacity: Int, mapper: V => K) = this(capacity, mapper, Nullable.empty)

  private val items: OrderedSet[V] = new OrderedSet[V](
    capacity,
    new CollectionHost[V] {
      override def adding(index: Int, v: Nullable[V], v2: Nullable[Object]): Unit = {
        if (ClassificationBag.this.host.isDefined && !ClassificationBag.this.host.get.skipHostUpdate()) {
          ClassificationBag.this.host.get.adding(index, v, v2)
        }
        if (v.isDefined) bag.addItem(v.get, index)
      }

      override def removing(index: Int, v: Nullable[V]): Nullable[Object] = {
        if (ClassificationBag.this.host.isDefined && !ClassificationBag.this.host.get.skipHostUpdate()) {
          ClassificationBag.this.host.get.removing(index, v)
        }
        if (v.isDefined) bag.removeItem(v.get, index)
        Nullable.empty
      }

      override def clearing(): Unit = {
        if (ClassificationBag.this.host.isDefined && !ClassificationBag.this.host.get.skipHostUpdate()) {
          ClassificationBag.this.host.get.clearing()
        }
        bag.clear()
      }

      override def addingNulls(index: Int): Unit =
        // nothing to be done, we're good
        if (ClassificationBag.this.host.isDefined && !ClassificationBag.this.host.get.skipHostUpdate()) {
          ClassificationBag.this.host.get.addingNulls(index)
        }

      override def skipHostUpdate(): Boolean = false

      override def getIteratorModificationCount: Int = getModificationCount
    }
  )

  private[collection] val bag: IndexedItemBitSetMap[K, V] = new IndexedItemBitSetMap[K, V](mapper)

  def getItems: OrderedSet[V] = items

  def getModificationCount: Int = items.getModificationCount

  def add(item: Nullable[V]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java interop — OrderedSet.add accepts nullable
    val raw = item.orNull
    items.add(raw)
  }

  def remove(item: Nullable[V]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java interop — OrderedSet.remove accepts nullable
    val raw = item.orNull
    items.remove(raw)
  }

  def remove(index: Int): Boolean = items.removeIndex(index)

  def contains(item: Nullable[V]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java interop — OrderedSet.contains accepts nullable
    val raw = item.orNull
    items.contains(raw)
  }

  def containsCategory(category: Nullable[K]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — get accepts nullable key
    val raw    = category.orNull
    val bitSet = bag.get(raw)
    bitSet != null && !bitSet.isEmpty // Java HashMap interop — get returns null for missing keys
  }

  def getCategorySet(category: Nullable[K]): Nullable[BitSet] = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — get accepts nullable key
    val raw = category.orNull
    Nullable(bag.get(raw))
  }

  def getCategoryCount(category: Nullable[K]): Int = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — get accepts nullable key
    val raw    = category.orNull
    val bitSet = bag.get(raw)
    if (bitSet == null) 0 else bitSet.cardinality() // Java HashMap interop — get returns null for missing keys
  }

  def getCategoryMap: java.util.Map[K, BitSet] = bag

  def clear(): Unit = items.clear()

  def getCategoryItems[X](xClass: Class[? <: X], categories: Array[K]): ReversibleIterable[X] =
    new IndexedIterable[X, V, ReversibleIterable[Int]](items.getConcurrentModsIndexedProxy(), new BitSetIterable(categoriesBitSet(categories), false))

  def getCategoryItems[X](xClass: Class[? <: X], categories: java.util.Collection[? <: K]): ReversibleIterable[X] =
    new IndexedIterable[X, V, ReversibleIterable[Int]](items.getConcurrentModsIndexedProxy(), new BitSetIterable(categoriesBitSet(categories), false))

  def getCategoryItems[X](xClass: Class[? <: X], bitSet: BitSet): ReversibleIterable[X] =
    new IndexedIterable[X, V, ReversibleIterable[Int]](items.getConcurrentModsIndexedProxy(), new BitSetIterable(bitSet, false))

  def getCategoryItemsReversed[X](xClass: Class[? <: X], categories: Array[K]): ReversibleIterable[X] =
    new IndexedIterable[X, V, ReversibleIterable[Int]](items.getConcurrentModsIndexedProxy(), new BitSetIterable(categoriesBitSet(categories), true))

  def getCategoryItemsReversed[X](xClass: Class[? <: X], categories: java.util.Collection[? <: K]): ReversibleIterable[X] =
    new IndexedIterable[X, V, ReversibleIterable[Int]](items.getConcurrentModsIndexedProxy(), new BitSetIterable(categoriesBitSet(categories), true))

  def getCategoryItemsReversed[X](xClass: Class[? <: X], bitSet: BitSet): ReversibleIterable[X] =
    new IndexedIterable[X, V, ReversibleIterable[Int]](items.getConcurrentModsIndexedProxy(), new BitSetIterable(bitSet, true))

  def categoriesBitSet(categories: Array[K]): BitSet = {
    val bitSet = new BitSet()
    for (category <- categories) {
      val bitSet1 = bag.get(category)
      if (bitSet1 != null) { // Java HashMap interop — get returns null for missing keys
        bitSet.or(bitSet1)
      }
    }
    bitSet
  }

  def categoriesBitSet(categories: java.util.Collection[? <: K]): BitSet = {
    val bitSet = new BitSet()
    val iter   = categories.iterator()
    while (iter.hasNext) {
      val category = iter.next()
      val bitSet1  = bag.get(category)
      if (bitSet1 != null) { // Java HashMap interop — get returns null for missing keys
        bitSet.or(bitSet1)
      }
    }
    bitSet
  }
}
