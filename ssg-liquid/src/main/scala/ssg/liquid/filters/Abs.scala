/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Abs.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Abs.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

/** Liquid "abs" filter.
  *
  * Everything that is not a number causes this filter to return 0.
  */
class Abs extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (isInteger(value) || canBeInteger(value)) {
      Math.abs(asNumber(value).longValue())
    } else if (isNumber(value) || canBeDouble(value)) {
      LValue.asFormattedNumber(PlainBigDecimal(asNumber(value).toString).abs())
    } else {
      0
    }
}
