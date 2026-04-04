/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/OrNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 */
package ssg
package liquid
package nodes

class OrNode(private val lhs: LNode, private val rhs: LNode) extends LValue with LNode {

  override def render(context: TemplateContext): Any =
    asBoolean(lhs.render(context)) || asBoolean(rhs.render(context))
}
