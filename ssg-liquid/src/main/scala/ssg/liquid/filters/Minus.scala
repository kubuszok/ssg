/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Minus.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Minus.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

/** Liquid "minus" filter — subtraction. */
class Minus extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView = {
    var v: DataView = value
    if (!isNumber(v)) {
      v = DataView.from(0L)
    }
    checkParams(params, 1)
    val rhsObj = params(0)
    if (canBeInteger(v) && canBeInteger(rhsObj)) {
      DataView.from(asNumber(v).longValue() - asNumber(rhsObj).longValue())
    } else {
      val first  = PlainBigDecimal(asNumber(v).toString)
      val second = PlainBigDecimal(asNumber(rhsObj).toString)
      DataView.from(LValue.asFormattedNumber(first.subtract(second)))
    }
  }
}
