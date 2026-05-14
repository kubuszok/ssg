/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Compact.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Compact.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

/** Liquid "compact" filter — removes null elements from an array. */
class Compact extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    if (!isArray(value)) {
      value
    } else {
      val values = asArray(value, context)
      DataView.from(values.filter(!_.isNull))
    }
}
