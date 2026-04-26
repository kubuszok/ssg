/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/H.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/H.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

class H extends Filter {

  /*
   * h(input)
   *
   * Alias for: escape
   */
  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    H.escapeFilter.apply(value, context, params)
}

object H {
  private val escapeFilter: Escape = new Escape()
}
