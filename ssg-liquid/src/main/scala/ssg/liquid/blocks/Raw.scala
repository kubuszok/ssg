/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Raw.java
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

/** Temporarily disables tag processing to avoid syntax conflicts. */
class Raw extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any =
    nodes(0).render(context)
}
