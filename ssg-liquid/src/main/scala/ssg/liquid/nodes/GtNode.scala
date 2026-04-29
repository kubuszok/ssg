/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/GtNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/GtNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import ssg.liquid.exceptions.IncompatibleTypeComparisonException

class GtNode(lhs: LNode, rhs: LNode) extends ComparingExpressionNode(lhs, rhs, true) {

  override protected def doCompare(a: Any, b: Any, strictTypedExpressions: Boolean): Any =
    if (a.isInstanceOf[Comparable[?]] && a.getClass.isInstance(b)) {
      a.asInstanceOf[Comparable[Any]].compareTo(b) > 0
    } else if (b.isInstanceOf[Comparable[?]] && b.getClass.isInstance(a)) {
      b.asInstanceOf[Comparable[Any]].compareTo(a) <= 0
    } else if (strictTypedExpressions) {
      throw new IncompatibleTypeComparisonException(a, b)
    } else {
      false
    }
}
