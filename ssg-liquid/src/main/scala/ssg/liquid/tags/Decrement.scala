/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/Decrement.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 */
package ssg
package liquid
package tags

import ssg.liquid.nodes.LNode

/** Creates a new number variable, and decreases its value by 1 every time decrement is called on the variable. The counter's initial value is -1.
  */
class Decrement extends Tag {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = {
    var value    = 0L
    val variable = asString(nodes(0).render(context), context)

    val environmentMap = context.getEnvironmentMap
    if (environmentMap.containsKey(variable)) {
      value = environmentMap.get(variable).asInstanceOf[Long]
    }

    value = value - 1L
    environmentMap.put(variable, java.lang.Long.valueOf(value))

    java.lang.Long.valueOf(value)
  }
}
