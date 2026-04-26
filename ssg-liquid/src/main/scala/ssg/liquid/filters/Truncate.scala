/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Truncate.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Truncate.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

class Truncate extends Filter {

  /*
   * truncate(input, length = 50, truncate_string = "...")
   *
   * Truncate a string down to x characters
   */
  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (value == null) {
      ""
    } else {
      val text           = asString(value, context)
      var length         = 50
      var truncateString = "..."

      if (params.length >= 1) {
        length = asNumber(get(0, params)).intValue()
      }

      if (params.length >= 2) {
        truncateString = asString(get(1, params), context)
      }

      // If the entire string fits untruncated, return the string.
      if (length >= text.length()) {
        text
      }
      // If the 'marker' takes up all the space, output the marker (even if
      // it's longer than the requested length).
      else if (truncateString.length() >= length) {
        truncateString
      } else {
        // Otherwise, output as much text as will fit.
        val remainingChars = length - truncateString.length()
        text.substring(0, remainingChars) + truncateString
      }
    }
}
