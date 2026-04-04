/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Capture.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.blocks → ssg.liquid.blocks
 */
package ssg
package liquid
package blocks

import ssg.liquid.nodes.LNode

/** Block tag that captures text into a variable. */
class Capture extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = {
    val id    = asString(nodes(0).render(context), context)
    val block = nodes(1)

    // Capture causes variable to be saved "globally"
    context.put(id, block.render(context), true)

    null
  }
}
