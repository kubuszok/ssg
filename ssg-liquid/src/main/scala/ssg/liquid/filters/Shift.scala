/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Shift.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Shift.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import java.util.{ ArrayList, Collections }

/** Liquid "shift" filter — returns a new array with the first item(s) removed.
  *
  * Jekyll-specific filter for array manipulation.
  */
class Shift extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else {
      val shiftIndex = params.length match {
        case 0 => 1
        case 1 =>
          val n = asNumber(params(0)).intValue()
          if (n < 0) {
            throw new RuntimeException("negative shift value")
          }
          n
        case _ =>
          throw new RuntimeException("shift supports up to 1 parameter")
      }

      val list = asList(value, context)
      val size = list.size()
      if (shiftIndex >= size) {
        Collections.emptyList()
      } else {
        new ArrayList[Any](list.subList(shiftIndex, size))
      }
    }
}
