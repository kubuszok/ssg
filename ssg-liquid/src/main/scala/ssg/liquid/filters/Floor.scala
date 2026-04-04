/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Floor.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

/** Liquid "floor" filter — rounds down to the nearest integer. */
class Floor extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isNumber(value)) {
      value
    } else {
      Math.floor(asNumber(value).doubleValue()).toLong
    }
}
