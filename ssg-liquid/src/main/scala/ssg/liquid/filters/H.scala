/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/H.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/H.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

class H extends Filter {

  /*
   * h(input)
   *
   * Alias for: escape
   */
  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    H.escapeFilter.apply(value, context, params)
}

object H {
  private val escapeFilter: Escape = new Escape()
}
