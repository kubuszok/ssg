/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/SubClassingBag.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/SubClassingBag.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable
import ssg.md.util.collection.iteration.ReversibleIterable

import java.util.BitSet

class SubClassingBag[T](
  private val items: ClassificationBag[Class[?], T],
  subClassMap:       java.util.HashMap[Class[?], java.util.List[Class[?]]]
) {

  private val _subClassMap: java.util.HashMap[Class[?], BitSet] = new java.util.HashMap[Class[?], BitSet]()

  // initialize from subClassMap
  {
    val iter = subClassMap.keySet().iterator()
    while (iter.hasNext) {
      val clazz     = iter.next()
      val classList = subClassMap.get(clazz)
      val bitSet    = items.categoriesBitSet(classList)
      if (!bitSet.isEmpty) {
        _subClassMap.put(clazz, bitSet)
      }
    }
  }

  def getItems: OrderedSet[T] = items.getItems

  def contains(item: Nullable[T]): Boolean = items.contains(item)

  def containsType(typeCls: Nullable[Class[?]]): Boolean = {
    @annotation.nowarn("msg=deprecated") // Java interop — ClassificationBag accepts nullable
    val raw = typeCls.orNull
    items.containsCategory(Nullable(raw))
  }

  def getTypeSet(category: Nullable[Class[?]]): Nullable[BitSet] = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — get accepts nullable key
    val raw = category.orNull
    Nullable(_subClassMap.get(raw))
  }

  def getTypeCount(category: Nullable[Class[?]]): Int = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — get accepts nullable key
    val raw    = category.orNull
    val bitSet = _subClassMap.get(raw)
    if (bitSet == null) 0 else bitSet.cardinality() // Java HashMap interop — get returns null for missing keys
  }

  def itemsOfType[X](xClass: Class[X], categories: Array[Class[?]]): ReversibleIterable[X] =
    items.getCategoryItems(xClass, typeBitSet(xClass, categories))

  def itemsOfType[X](xClass: Class[X], categories: java.util.Collection[Class[?]]): ReversibleIterable[X] =
    items.getCategoryItems(xClass, typeBitSet(xClass, categories))

  def reversedItemsOfType[X](xClass: Class[X], categories: Array[Class[?]]): ReversibleIterable[X] =
    items.getCategoryItemsReversed(xClass, typeBitSet(xClass, categories))

  def reversedItemsOfType[X](xClass: Class[X], categories: java.util.Collection[Class[?]]): ReversibleIterable[X] =
    items.getCategoryItemsReversed(xClass, typeBitSet(xClass, categories))

  def typeBitSet(xClass: Class[?], categories: Array[Class[?]]): BitSet = {
    val bitSet = new BitSet()
    for (category <- categories)
      if (xClass.isAssignableFrom(category) && _subClassMap.containsKey(category)) {
        bitSet.or(_subClassMap.get(category))
      }
    bitSet
  }

  def typeBitSet(xClass: Class[?], categories: java.util.Collection[Class[?]]): BitSet = {
    val bitSet = new BitSet()
    val iter   = categories.iterator()
    while (iter.hasNext) {
      val category = iter.next()
      if (xClass.isAssignableFrom(category) && _subClassMap.containsKey(category)) {
        bitSet.or(_subClassMap.get(category))
      }
    }
    bitSet
  }
}
