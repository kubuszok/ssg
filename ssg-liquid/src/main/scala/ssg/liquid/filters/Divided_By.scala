/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Divided_By.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Divided_By.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

/** Liquid "divided_by" filter — division. */
class Divided_By extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    var v: Any = value
    if (v == null) {
      v = 0L
    }
    checkParams(params, 1)
    val rhsObj = params(0)
    if (canBeInteger(v) && canBeInteger(rhsObj)) {
      asNumber(v).longValue() / asNumber(rhsObj).longValue()
    } else {
      asNumber(v).doubleValue() / asNumber(rhsObj).doubleValue()
    }
  }
}
