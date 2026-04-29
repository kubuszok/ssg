/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/NEqNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/NEqNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

class NEqNode(lhs: LNode, rhs: LNode) extends ComparingExpressionNode(lhs, rhs, false) {

  override protected def doCompare(a: Any, b: Any, strictTypedExpressions: Boolean): Any =
    if (a.isInstanceOf[Boolean] && b.isInstanceOf[Boolean]) {
      a != b
    } else if (a.isInstanceOf[Boolean]) {
      true
    } else if (b.isInstanceOf[Boolean]) {
      true
    } else {
      !LValue.areEqual(a, b)
    }
}
