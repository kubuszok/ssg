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
 * Covenant-java-reference: src/main/java/liqp/filters/Round.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

import java.math.{ BigDecimal, RoundingMode }

/** Liquid "round" filter — rounds to the nearest integer or specified decimal places. */
class Round extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    if (!canBeDouble(value)) {
      DataView.from(0)
    } else {
      val number = asNumber(value).doubleValue()
      var round  = 0L

      if (params.length > 0 && canBeDouble(params(0))) {
        round = asNumber(params(0)).longValue()
      }

      // liqp Round.java:26-32: decimal places appended only when round > 0;
      // round <= 0 uses scale 0 (nearest integer, HALF_UP).
      val scale = if (round > 0) round.toInt else 0
      val bd    = new BigDecimal(number.toString).setScale(scale, RoundingMode.HALF_UP)
      DataView.from(PlainBigDecimal(bd))
    }
}
