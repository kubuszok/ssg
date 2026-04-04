/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Unless.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.blocks → ssg.liquid.blocks
 *   Idiom: boundary/break for early return
 */
package ssg
package liquid
package blocks

import ssg.liquid.nodes.LNode

import scala.util.boundary
import scala.util.boundary.break

/** Mirror of if statement — renders if condition is false. */
class Unless extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any =
    boundary {
      var i = 0
      while (i < nodes.length - 1) {
        val exprNodeValue = nodes(i).render(context)
        val blockNode     = nodes(i + 1)
        if (!asBoolean(exprNodeValue)) {
          break(blockNode.render(context))
        }
        i += 2
      }
      ""
    }
}
