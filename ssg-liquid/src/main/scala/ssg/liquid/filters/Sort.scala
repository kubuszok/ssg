/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Sort.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Sort.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

import scala.collection.immutable.VectorMap

/** Liquid "sort" filter — sorts elements of an array.
  *
  * Supports an optional property parameter for sorting arrays of hashes/drops.
  */
class Sort extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    if (value.isNull) {
      DataView.from("")
    } else {
      val property: String = if (params.length == 0) null else asString(params(0), context)

      if (isMap(value)) {
        val map = asMap(value)
        if (map.isEmpty) value
        else {
          // Sort map entries by key
          val sorted = map.toVector.sortBy(_._1)
          DataView.from(VectorMap.from(sorted))
        }
      } else if (isArray(value)) {
        val array = asArray(value, context)
        if (array.isEmpty) value
        else sortImpl(array, context, property)
      } else {
        throw new RuntimeException("cannot sort: " + value + "; type:" + (if (value.isNull) "null" else value.view.getClass.toString))
      }
    }

  private def sortImpl(array: Vector[DataView], context: TemplateContext, property: String): DataView = {
    val sorted =
      if (property == null) {
        array.sortWith { (a, b) =>
          compareDataViews(a, b) < 0
        }
      } else {
        array.sortWith { (a, b) =>
          val av = getProperty(a, property)
          val bv = getProperty(b, property)
          compareDataViews(av, bv) < 0
        }
      }
    DataView.from(sorted)
  }

  private def getProperty(dv: DataView, property: String): DataView =
    if (dv.isNull) DataView.nil
    else
      dv.view match {
        case m: VectorMap[?, ?] =>
          m.asInstanceOf[VectorMap[String, DataView]].getOrElse(property, DataView.nil)
        case _ => DataView.nil
      }

  @SuppressWarnings(Array("unchecked"))
  private def compareDataViews(a: DataView, b: DataView): Int =
    if (a.isNull && b.isNull) 0
    else if (a.isNull) -1
    else if (b.isNull) 1
    else {
      val av = a.view
      val bv = b.view
      if (av.isInstanceOf[Comparable[?]] && av.getClass.isInstance(bv)) {
        av.asInstanceOf[Comparable[Any]].compareTo(bv)
      } else {
        a.toString.compareTo(b.toString)
      }
    }
}
