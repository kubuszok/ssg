/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Reverse.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

import java.util.{ ArrayList, Arrays, Collections }

/** Liquid "reverse" filter — reverses the order of items in an array. */
class Reverse extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else {
      val values = asArray(value, context)
      val list   = new ArrayList[Any](Arrays.asList(values*))
      Collections.reverse(list)
      list.toArray()
    }
}
