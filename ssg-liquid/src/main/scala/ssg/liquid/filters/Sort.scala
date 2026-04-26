/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Sort.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Sort.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import ssg.liquid.parser.Inspectable

import java.util.{ ArrayList, Collections, HashMap, LinkedHashMap, List => JList, Map => JMap }

/** Liquid "sort" filter — sorts elements of an array.
  *
  * Supports an optional property parameter for sorting arrays of hashes/drops.
  */
class Sort extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (value == null) {
      ""
    } else {
      val property: String = if (params.length == 0) null else asString(params(0), context)

      var v: Any = value
      var wasMap = false

      v match {
        case m: JMap[?, ?] =>
          if (m.isEmpty) {
            v
          } else {
            val list = new ArrayList[Any]()
            val it   = m.asInstanceOf[JMap[Any, Any]].entrySet().iterator()
            while (it.hasNext) {
              val en = it.next()
              list.add(new ComparableMapEntry[Any, Any](en))
            }
            v = list
            wasMap = true
            sortImpl(v, context, property, wasMap)
          }
        case col: java.util.Collection[?] =>
          if (col.isEmpty) {
            v
          } else {
            sortImpl(v, context, property, wasMap)
          }
        case _ =>
          if (!isArray(v)) {
            throw new RuntimeException("cannot sort: " + v + "; type:" + (if (v == null) "null" else v.getClass.toString))
          }
          sortImpl(v, context, property, wasMap)
      }
    }

  private def sortImpl(v: Any, context: TemplateContext, property: String, wasMap: Boolean): Any = {
    val array = asArray(v, context)
    val list  = asComparableList(context, array, property)
    Collections.sort(list)

    if (wasMap) {
      val map = new LinkedHashMap[Any, Any]()
      val it  = list.iterator()
      while (it.hasNext) {
        val en = it.next().asInstanceOf[ComparableMapEntry[Any, Any]]
        map.put(en.getKey, en.getValue)
      }
      map
    } else if (property == null) {
      list.toArray(new Array[Comparable[?]](list.size()))
    } else {
      list.toArray(new Array[Sort.SortableMap](list.size()))
    }
  }

  @SuppressWarnings(Array("unchecked"))
  private def asComparableList(context: TemplateContext, array: Array[Any], property: String): JList[Comparable[Any]] = {
    val list = new ArrayList[Comparable[Any]]()
    for (obj <- array) {
      var element: Any = obj
      if (property != null && element.isInstanceOf[Inspectable]) {
        val evaluated = context.parser.evaluate(element)
        element = evaluated.toLiquid()
      }
      element match {
        case m: JMap[?, ?] if property != null =>
          list.add(new Sort.SortableMap(m.asInstanceOf[JMap[String, Comparable[Any]]], property))
        case c: Comparable[?] =>
          list.add(c.asInstanceOf[Comparable[Any]])
        case _ =>
          list.add(element.asInstanceOf[Comparable[Any]])
      }
    }
    list
  }
}

object Sort {

  /** A HashMap that is also Comparable, used for sorting arrays of maps by a property key. */
  class SortableMap(map: JMap[String, Comparable[Any]], val property: String) extends HashMap[String, Comparable[Any]] with Comparable[Any] {

    putAll(map)

    override def compareTo(that: Any): Int = {
      val thisValue = get(property)
      val thatValue = that match {
        case sm: SortableMap => sm.get(property)
        case other => other
      }
      if (thisValue == null || thatValue == null) {
        throw new RuntimeException("Liquid error: comparison of Hash with Hash failed")
      }
      thisValue.compareTo(thatValue.asInstanceOf[Any])
    }

    override def toString: String = {
      val builder = new StringBuilder()
      val it      = entrySet().iterator()
      while (it.hasNext) {
        val entry = it.next()
        builder.append(entry.getKey).append(entry.getValue)
      }
      builder.toString()
    }
  }
}

/** A Map.Entry wrapper that is Comparable by key, used for sorting Maps. */
private class ComparableMapEntry[K, V](private val entry: JMap.Entry[K, V]) extends JMap.Entry[K, V] with Comparable[JMap.Entry[K, V]] {

  override def getKey:             K = entry.getKey
  override def getValue:           V = entry.getValue
  override def setValue(value: V): V = entry.setValue(value)

  @SuppressWarnings(Array("unchecked"))
  override def compareTo(o: JMap.Entry[K, V]): Int =
    getKey.asInstanceOf[Comparable[Any]].compareTo(o.getKey)

  override def toString: String = entry.toString
}
