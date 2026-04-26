/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Map.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Map.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import ssg.liquid.parser.Inspectable

import java.util.{ ArrayList, Map => JMap }

/** Liquid "map" filter — extracts a property from each element of an array.
  *
  * Renamed to MapFilter to avoid conflict with scala.collection.Map.
  */
class MapFilter extends Filter("map") {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (value == null) {
      ""
    } else {
      val list  = new ArrayList[Any]()
      val array = asArray(value, context)
      val key   = asString(get(0, params), context)

      for (obj <- array) {
        val map: JMap[?, ?] = obj match {
          case insp: Inspectable =>
            val evaluated = context.parser.evaluate(insp)
            evaluated.toLiquid()
          case m: JMap[?, ?] => m
          case _ => obj.asInstanceOf[JMap[?, ?]]
        }
        val v = map.get(key)
        if (v != null) {
          list.add(v)
        }
      }
      list.toArray(new Array[AnyRef](list.size()))
    }
}
