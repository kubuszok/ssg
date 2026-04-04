/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Split.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaced look-behind regex with manual split for JS compatibility
 */
package ssg
package liquid
package filters

import java.util.regex.Pattern

class Split extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val original = asString(value, context)
    if (original.isEmpty) {
      new Array[String](0)
    } else {
      val delimiter = asString(get(0, params), context)
      if (delimiter.isEmpty) {
        // Empty delimiter: return the whole string as a single-element array
        Array(original)
      } else {
        // Split without removing leading empty string (cross-platform, no look-behind)
        val parts = original.split(Pattern.quote(delimiter), -1)
        // Ruby/Liquid split doesn't produce a leading empty element from position 0
        // but does preserve trailing empties with -1 limit
        parts
      }
    }
  }
}
