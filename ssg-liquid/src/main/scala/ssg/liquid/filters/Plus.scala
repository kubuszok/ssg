/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Plus.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

/** Liquid "plus" filter — addition. */
class Plus extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    var v: Any = value
    if (!isNumber(v)) {
      v = 0
    }
    checkParams(params, 1)
    val rhsObj = params(0)
    if (canBeInteger(v) && canBeInteger(rhsObj)) {
      asNumber(v).longValue() + asNumber(rhsObj).longValue()
    } else {
      val first  = PlainBigDecimal(asNumber(v).toString)
      val second = PlainBigDecimal(asNumber(rhsObj).toString)
      LValue.asFormattedNumber(first.add(second))
    }
  }
}
