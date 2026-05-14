/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/where/LiquidWhereImpl.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/where/LiquidWhereImpl.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters
package where

import ssg.data.DataView

import scala.collection.immutable.VectorMap

class LiquidWhereImpl(
  templateContext: TemplateContext,
  helper:          PropertyResolverHelper
) extends WhereImpl(templateContext, helper) {

  override def apply(input: DataView, params: Array[DataView]): DataView = {
    val objects = toVector(input)
    if (objects.isEmpty) {
      DataView.from(Vector.empty[DataView])
    } else {
      val res = objects.filter(el => objectHasPropertyValue(el, params))
      DataView.from(res)
    }
  }

  private def objectHasPropertyValue(el: DataView, params: Array[DataView]): Boolean = {
    val rawProperty = params(0)
    val property    = asString(rawProperty, context)
    val resolver    = resolverHelper.findFor(el)
    val node: DataView =
      if (resolver != null) {
        resolver.getItemProperty(context, el, rawProperty)
      } else if (isMap(el)) {
        val map = asMap(el)
        if (!map.contains(property)) DataView.nil
        else map(property)
      } else {
        DataView.nil
      }

    if (params.length == 1) {
      asBoolean(node)
    } else {
      val value = params(1)
      LValue.areEqual(node, value) || (node.toString == value.toString)
    }
  }

  private def toVector(in: DataView): Vector[DataView] =
    if (in.isNull) {
      Vector.empty
    } else
      in.view match {
        case v: Vector[?]       => flatten(v.asInstanceOf[Vector[DataView]])
        case _: VectorMap[?, ?] => Vector(in) // map can be also a collection, but we treat it as hash
        case _ => Vector(in)
      }

  private def flatten(vec: Vector[DataView]): Vector[DataView] =
    vec.flatMap { dv =>
      if (dv.isNull) Vector.empty
      else
        dv.view match {
          case v: Vector[?] => flatten(v.asInstanceOf[Vector[DataView]])
          case _ => Vector(dv)
        }
    }
}
