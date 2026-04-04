/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Remove.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

class Remove extends Filter {

  /*
   * remove(input, string)
   *
   * remove a substring
   */
  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val original = asString(value, context)
    val needle   = get(0, params)
    if (needle == null) {
      throw new RuntimeException("invalid pattern: " + needle)
    }
    original.replace(String.valueOf(needle), "")
  }
}
