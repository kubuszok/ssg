/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Replace_First.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Replace_First.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import java.util.regex.{ Matcher, Pattern }

class Replace_First extends Filter {

  /*
   * replace_first(input, string, replacement = '')
   *
   * Replace the first occurrences of a string with another
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
    original.replaceFirst(Pattern.quote(String.valueOf(needle)), Matcher.quoteReplacement(replacement))
  }
}
