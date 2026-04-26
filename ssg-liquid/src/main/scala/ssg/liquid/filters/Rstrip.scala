/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Rstrip.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Rstrip.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

class Rstrip extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isString(value)) {
      value
    } else {
      asString(value, context).replaceAll("\\s+$", "")
    }
}
