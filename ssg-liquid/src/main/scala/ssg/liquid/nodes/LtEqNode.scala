/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/LtEqNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/LtEqNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import ssg.data.DataView
import ssg.liquid.exceptions.IncompatibleTypeComparisonException

class LtEqNode(lhs: LNode, rhs: LNode) extends ComparingExpressionNode(lhs, rhs, true) {

  override protected def doCompare(a: DataView, b: DataView, strictTypedExpressions: Boolean): DataView = {
    val av = if (a.isNull) null else a.view
    val bv = if (b.isNull) null else b.view
    DataView.from(
      if (av.isInstanceOf[Comparable[?]] && av.getClass.isInstance(bv)) {
        av.asInstanceOf[Comparable[Any]].compareTo(bv) <= 0
      } else if (bv.isInstanceOf[Comparable[?]] && bv.getClass.isInstance(av)) {
        bv.asInstanceOf[Comparable[Any]].compareTo(av) > 0
      } else if (strictTypedExpressions) {
        throw new IncompatibleTypeComparisonException(av, bv)
      } else {
        false
      }
    )
  }
}
