/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Round.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaced java.text.DecimalFormat with java.math.BigDecimal
 *               for cross-platform compatibility (JS/Native)
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Round.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import java.math.{ BigDecimal, RoundingMode }

/** Liquid "round" filter — rounds to the nearest integer or specified decimal places. */
class Round extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!canBeDouble(value)) {
      0
    } else {
      val number = asNumber(value).doubleValue()
      var scale  = 0

      if (params.length > 0 && canBeDouble(params(0))) {
        scale = asNumber(params(0)).intValue()
      }

      val bd = new BigDecimal(number.toString).setScale(scale, RoundingMode.HALF_UP)
      PlainBigDecimal(bd)
    }
}
