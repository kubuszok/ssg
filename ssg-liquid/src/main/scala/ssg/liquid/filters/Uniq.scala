/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Uniq.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Uniq.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import java.util.{ Arrays, LinkedHashSet }

/** Liquid "uniq" filter — removes duplicate elements from an array. */
class Uniq extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else {
      val set = new LinkedHashSet[Any](Arrays.asList(asArray(value, context)*))
      set.toArray()
    }
}
