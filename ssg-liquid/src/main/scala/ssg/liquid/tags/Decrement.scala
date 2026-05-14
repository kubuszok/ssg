/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/Decrement.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/tags/Decrement.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package tags

import ssg.data.DataView
import ssg.liquid.nodes.LNode

class Decrement extends Tag {

  override def render(context: TemplateContext, nodes: Array[LNode]): DataView = {
    var value    = 0L
    val variable = asString(nodes(0).render(context), context)

    val environmentMap = context.getEnvironmentMap
    if (environmentMap.containsKey(variable)) {
      val stored = environmentMap.get(variable)
      value = stored.asLong.getOrElse(0L)
    }

    value = value - 1L
    environmentMap.put(variable, DataView.from(value))

    DataView.from(value)
  }
}
