/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Remove_First.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Remove_First.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import java.util.regex.Pattern

class Remove_First extends Filter {

  /*
   * remove_first(input, string)
   *
   * remove the first occurrences of a substring
   */
  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val original = asString(value, context)
    val needle   = get(0, params)
    if (needle == null) {
      throw new RuntimeException("invalid pattern: " + needle)
    }
    original.replaceFirst(Pattern.quote(String.valueOf(needle)), "")
  }
}
