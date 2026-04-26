/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Pop.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Pop.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import java.util.{ ArrayList, Collections }

/** Liquid "pop" filter — returns a new array with the last item(s) removed.
  *
  * Jekyll-specific filter for array manipulation.
  */
class Pop extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else {
      val numPop = params.length match {
        case 0 => 1
        case 1 =>
          val n = asNumber(params(0)).intValue()
          if (n < 0) {
            throw new RuntimeException("negative pop value")
          }
          n
        case _ =>
          throw new RuntimeException("pop supports up to 1 parameter")
      }

      val list          = asList(value, context)
      val remainingSize = list.size() - numPop

      if (remainingSize <= 0) {
        Collections.emptyList()
      } else {
        new ArrayList[Any](list.subList(0, remainingSize))
      }
    }
}
