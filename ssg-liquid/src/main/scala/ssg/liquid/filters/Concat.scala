/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Concat.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Concat.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

/** Liquid "concat" filter — concatenates two arrays. */
class Concat extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView = {
    checkParams(params, 1)
    if (!isArray(params(0))) {
      throw new RuntimeException("Liquid error: concat filter requires an array argument")
    }
    val left  = asArray(value, context)
    val right = asArray(params(0), context)
    DataView.from(left ++ right)
  }
}
