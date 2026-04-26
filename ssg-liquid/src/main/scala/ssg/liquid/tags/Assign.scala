/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/Assign.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/tags/Assign.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package tags

import ssg.liquid.nodes.{ FilterNode, LNode }

/** Assigns some value to a variable. */
class Assign extends Tag {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = {
    val id         = String.valueOf(nodes(0).render(context))
    val expression = nodes(1)
    var value: Any = expression.render(context)

    var i = 2
    while (i < nodes.length) {
      val filter = nodes(i).asInstanceOf[FilterNode]
      value = filter.apply(value, context)
      i += 1
    }

    // Assign causes variable to be saved "globally"
    context.put(id, value, true)

    ""
  }
}
