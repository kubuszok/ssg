/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Map.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Map.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

import scala.collection.immutable.VectorMap

/** Liquid "map" filter — extracts a property from each element of an array. */
class MapFilter extends Filter("map") {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    if (value.isNull) {
      DataView.from("")
    } else {
      val array = value.view match {
        case _: VectorMap[?, ?] => Vector(value)
        case _ => asArray(value, context)
      }
      val key = asString(get(0, params), context)

      val result = array.flatMap { dv =>
        if (dv.isNull) None
        else
          dv.view match {
            case m: VectorMap[?, ?] =>
              m.asInstanceOf[VectorMap[String, DataView]].get(key)
            case _ => None
          }
      }
      DataView.from(result)
    }
}
