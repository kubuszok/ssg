/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/where/JekyllWhereImpl.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/where/JekyllWhereImpl.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters
package where

import ssg.data.DataView
import ssg.liquid.nodes.AtomNode

import scala.util.boundary
import scala.util.boundary.break

class JekyllWhereImpl(
  templateContext: TemplateContext,
  helper:          PropertyResolverHelper
) extends WhereImpl(templateContext, helper) {

  override def apply(input: DataView, params: Array[DataView]): DataView =
    if (params.length < 1) {
      input
    } else {
      val property = params(0)
      if (isFalsy(property, context)) {
        input
      } else {
        var value: DataView = DataView.nil
        if (params.length > 1) {
          value = params(1)
        }
        if (!value.isNull) {
          // arrays and maps as value → return input unchanged
          if (isArray(value) || isMap(value)) {
            input
          } else {
            filterInput(input, property, value)
          }
        } else {
          filterInput(input, property, value)
        }
      }
    }

  private def filterInput(input: DataView, property: DataView, value: DataView): DataView =
    if (input.isNull) {
      DataView.from("")
    } else if (!isArray(input) && !isMap(input)) {
      input
    } else {
      val items: Vector[DataView] =
        if (isMap(input)) {
          val map = asMap(input)
          map.values.toVector
        } else {
          asArray(input, context)
        }

      val res = items.filter { item =>
        val itemProperty = getItemProperty(item, property)
        comparePropertyVsTarget(itemProperty, value)
      }
      DataView.from(res)
    }

  private def comparePropertyVsTarget(itemProperty: DataView, target: DataView): Boolean =
    if (target.isNull) {
      itemProperty.isNull
    } else if (AtomNode.isEmpty(target) || AtomNode.isBlank(target)) {
      "".equals(asString(itemProperty, context)) || "".equals(joinedArray(itemProperty))
    } else {
      val strTarget = asString(target, context)
      if (isString(itemProperty)) {
        strTarget.equals(asString(itemProperty, context))
      } else {
        boundary {
          val objects = asArray(itemProperty, context)
          var i       = 0
          while (i < objects.size) {
            if (asString(objects(i), context).equals(strTarget)) break(true)
            i += 1
          }
          false
        }
      }
    }

  private def joinedArray(itemProperty: DataView): String = {
    val objects =
      if (isMap(itemProperty)) {
        mapAsVector(asMap(itemProperty))
      } else asArray(itemProperty, context)
    val sb = new StringBuilder()
    objects.foreach(dv => sb.append(asString(dv, context)))
    sb.toString()
  }

  private def getItemProperty(e: DataView, property: DataView): DataView = {
    val adapter = resolverHelper.findFor(e)
    if (adapter != null) {
      parseSortInput(adapter.getItemProperty(context, e, property))
    } else if (isMap(e)) {
      val map = asMap(e)
      val key = asString(property, context)
      parseSortInput(map.getOrElse(key, DataView.nil))
    } else {
      DataView.nil
    }
  }

  private def parseSortInput(property: DataView): DataView =
    if (property.isNull) property
    else
      property.view match {
        case s: String =>
          try
            DataView.from(java.lang.Double.parseDouble(s))
          catch {
            case _: Exception => property
          }
        case _ => property
      }
}
