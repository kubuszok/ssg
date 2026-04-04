/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Capitalize.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

class Capitalize extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val str = asString(value, context)
    if (str.isEmpty) {
      str
    } else {
      str.charAt(0).toUpper.toString + str.substring(1)
    }
  }
}
