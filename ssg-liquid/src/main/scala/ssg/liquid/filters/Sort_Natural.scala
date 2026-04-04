/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Sort_Natural.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

import java.util.{ ArrayList, Arrays, Collections, Comparator }

/** Liquid "sort_natural" filter — sorts elements case-insensitively. */
class Sort_Natural extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else {
      val array = asArray(value, context)
      val list  = new ArrayList[Any](Arrays.asList(array*))
      Collections.sort(
        list,
        new Comparator[Any] {
          override def compare(o1: Any, o2: Any): Int =
            String.valueOf(o1).compareToIgnoreCase(String.valueOf(o2))
        }
      )
      list.toArray()
    }
}
