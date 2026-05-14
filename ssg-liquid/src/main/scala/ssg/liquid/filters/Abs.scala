/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Abs.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Abs.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

/** Liquid "abs" filter.
  *
  * Everything that is not a number causes this filter to return 0.
  */
class Abs extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    if (isInteger(value) || canBeInteger(value)) {
      DataView.from(Math.abs(asNumber(value).longValue()))
    } else if (isNumber(value) || canBeDouble(value)) {
      DataView.from(LValue.asFormattedNumber(PlainBigDecimal(asNumber(value).toString).abs()))
    } else {
      DataView.from(0)
    }
}
