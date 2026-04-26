/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Escape.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Escape.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

class Escape extends Filter {

  /*
   * escape(input)
   *
   * escape a string
   */
  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val str = asString(value, context)
    str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
  }
}
