/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/ItemFactoryMap.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable

import java.util.function.{ Function => JFunction }

class ItemFactoryMap[I, P](protected val param: P, capacity: Int) extends java.util.Map[JFunction[P, I], I] {

  def this(param: P) = this(param, 0)

  protected val itemMap: java.util.HashMap[JFunction[P, I], I] = new java.util.HashMap[JFunction[P, I], I](capacity)

  def getItem(factory: JFunction[P, I]): I = {
    var item = itemMap.get(factory)
    if (item == null) { // Java HashMap interop — get returns null for missing keys
      item = factory.apply(param)
      itemMap.put(factory, item)
    }
    item
  }

  override def get(o: Any): I =
    o match {
      case f: JFunction[?, ?] =>
        getItem(f.asInstanceOf[JFunction[P, I]])
      case _ =>
        @annotation.nowarn("msg=deprecated") // Java Map interop — returning null for missing key
        val n = Nullable.empty[I].orNull
        n
    }

  override def size(): Int = itemMap.size()

  override def isEmpty: Boolean = itemMap.isEmpty

  override def containsKey(o: Any): Boolean = itemMap.containsKey(o)

  override def put(factory: JFunction[P, I], i: I): I = itemMap.put(factory, i)

  override def putAll(map: java.util.Map[? <: JFunction[P, I], ? <: I]): Unit = itemMap.putAll(map)

  override def remove(o: Any): I = itemMap.remove(o)

  override def clear(): Unit = itemMap.clear()

  override def containsValue(o: Any): Boolean = itemMap.containsValue(o)

  override def keySet(): java.util.Set[JFunction[P, I]] = itemMap.keySet()

  override def values(): java.util.Collection[I] = itemMap.values()

  override def entrySet(): java.util.Set[java.util.Map.Entry[JFunction[P, I], I]] = itemMap.entrySet()
}
