/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Replace.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

class Replace extends Filter {

  /*
   * replace(input, string, replacement = '')
   *
   * Replace occurrences of a string with another
   */
  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val original = asString(value, context)
    val needle   = get(0, params)
    if (needle == null) {
      throw new RuntimeException("invalid pattern: " + needle)
    }
    var replacement = ""
    if (params.length >= 2) {
      val obj = get(1, params)
      if (obj == null) {
        throw new RuntimeException("invalid replacement: " + needle)
      }
      replacement = asString(get(1, params), context)
    }
    original.replace(String.valueOf(needle), String.valueOf(replacement))
  }
}
