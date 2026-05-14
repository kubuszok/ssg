/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/Assign.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/tags/Assign.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package tags

import ssg.data.DataView
import ssg.liquid.nodes.{ FilterNode, LNode }

class Assign extends Tag {

  override def render(context: TemplateContext, nodes: Array[LNode]): DataView = {
    val id         = nodes(0).render(context).toString
    val expression = nodes(1)
    var value: DataView = expression.render(context)

    var i = 2
    while (i < nodes.length) {
      val filter = nodes(i).asInstanceOf[FilterNode]
      value = filter.apply(value, context)
      i += 1
    }

    context.put(id, value, true)

    DataView.from("")
  }
}
