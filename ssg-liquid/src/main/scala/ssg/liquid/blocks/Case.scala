/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Case.java
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

import ssg.liquid.nodes.{ BlockNode, LNode }

import scala.util.boundary
import scala.util.boundary.break

/** Standard case/when/else block. */
class Case extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = {
    //        ^(CASE condition           var
    //            ^(WHEN term+ block)    1,2,3  b1
    //            ^(ELSE block?))               b2

    val condition = nodes(0).render(context)

    boundary {
      var i = 1
      while (i < nodes.length) {
        var node = nodes(i)

        if (i == nodes.length - 1 && node.isInstanceOf[BlockNode]) {
          // this must be the trailing (optional) else-block
          break(node.render(context))
        } else {
          var hit = false

          // Iterate through the list of terms and stop when we encounter a BlockNode
          while (!node.isInstanceOf[BlockNode]) {
            val whenExpressionValue = node.render(context)
            if (LValue.areEqual(condition, whenExpressionValue)) {
              hit = true
            }
            i += 1
            node = nodes(i)
          }

          if (hit) {
            break(node.render(context))
          }
        }
        i += 1
      }
      null
    }
  }
}
