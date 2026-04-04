/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Prepend.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

class Prepend extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    asString(get(0, params), context) + asString(value, context)
}
