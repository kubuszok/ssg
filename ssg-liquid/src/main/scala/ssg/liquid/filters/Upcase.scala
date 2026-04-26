/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Upcase.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Upcase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

class Upcase extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    asString(value, context).toUpperCase()
}
