/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/EqNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 */
package ssg
package liquid
package nodes

class EqNode(lhs: LNode, rhs: LNode) extends ComparingExpressionNode(lhs, rhs, false) {

  override protected def doCompare(a: Any, b: Any, strictTypedExpressions: Boolean): Any =
    if (a == null) {
      b == null
    } else if (b == null) {
      false
    } else if (a.isInstanceOf[Boolean] && b.isInstanceOf[Boolean]) {
      a == b
    } else if (a.isInstanceOf[Boolean]) {
      false
    } else if (b.isInstanceOf[Boolean]) {
      false
    } else {
      LValue.areEqual(a, b)
    }
}
