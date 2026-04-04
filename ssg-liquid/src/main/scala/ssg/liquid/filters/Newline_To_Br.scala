/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Newline_To_Br.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

class Newline_To_Br extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    asString(value, context).replaceAll("[\\n]", "<br />\n")
}
