/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/At_Least.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/At_Least.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

/** Liquid "at_least" filter — limits a number to a minimum value. */
class At_Least extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (params == null || params.length == 0) {
      value
    } else if (!isNumber(value) || !isNumber(params(0))) {
      value
    } else {
      val numberValue = asNumber(value)
      val paramValue  = asNumber(params(0))
      if (numberValue.doubleValue() < paramValue.doubleValue()) {
        paramValue
      } else {
        numberValue
      }
    }
}
