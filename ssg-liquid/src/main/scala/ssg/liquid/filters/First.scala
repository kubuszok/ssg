/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/First.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/First.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

/** Liquid "first" filter — get the first element of an array. */
class First extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val array = asArray(value, context)
    if (array.length == 0) null else array(0)
  }
}
