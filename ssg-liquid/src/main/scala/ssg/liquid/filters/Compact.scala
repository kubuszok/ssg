/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Compact.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Compact.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import java.util.ArrayList

/** Liquid "compact" filter — removes null elements from an array. */
class Compact extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else {
      val values    = asArray(value, context)
      val compacted = new ArrayList[Any]()
      for (obj <- values)
        if (obj != null) {
          compacted.add(obj)
        }
      compacted.toArray()
    }
}
