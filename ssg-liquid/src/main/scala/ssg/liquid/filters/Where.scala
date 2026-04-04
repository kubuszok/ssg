/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Where.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Convention: Simplified where filter — inline implementation instead of delegate classes
 */
package ssg
package liquid
package filters

import ssg.liquid.parser.Inspectable

import java.util.{ ArrayList, List => JList, Map => JMap }

/** Filters an array of objects by a property value.
  *
  * Shopify Liquid and Jekyll have slightly different where semantics.
  */
class Where extends Filter("where") {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else {
      val items    = asArray(value, context)
      val property = asString(params(0), context)

      if (context.parser.liquidStyleWhere) {
        // Liquid style: where property [, value]
        val targetValue = if (params.length > 1) params(1) else null
        filterItems(items, property, targetValue, context, targetValue != null)
      } else {
        // Jekyll style: where property, value (required)
        checkParams(params, 2)
        val targetValue = params(1)
        filterItems(items, property, targetValue, context, true)
      }
    }

  private def filterItems(items: Array[Any], property: String, targetValue: Any, context: TemplateContext, matchValue: Boolean): JList[Any] = {
    val result = new ArrayList[Any]()
    for (item <- items) {
      val propValue = getProperty(item, property, context)
      if (matchValue) {
        if (LValue.areEqual(propValue, targetValue)) {
          result.add(item)
        }
      } else {
        // Liquid style without value: filter truthy
        if (propValue != null && propValue != java.lang.Boolean.FALSE) {
          result.add(item)
        }
      }
    }
    result
  }

  private def getProperty(item: Any, property: String, context: TemplateContext): Any =
    item match {
      case map:  JMap[?, ?]  => map.get(property)
      case insp: Inspectable =>
        val evaluated = context.parser.evaluate(insp)
        evaluated.toLiquid().get(property)
      case _ => null
    }
}
