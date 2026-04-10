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
        // Split preserving trailing empties (limit = -1), cross-platform (no look-behind)
        val parts = original.split(Pattern.quote(delimiter), -1)
        // The original Java used (?<!^) look-behind to avoid splitting at position 0.
        // Look-behinds aren't supported on Scala Native (re2), so we strip the leading
        // empty string in post-processing instead.
        if (parts.length > 0 && parts(0).isEmpty) {
          java.util.Arrays.copyOfRange(parts, 1, parts.length)
        } else {
          parts
        }
      }
    }
  }
}
