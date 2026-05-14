/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/ComparingExpressionNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *   Idiom: Optional → Option, abstract method
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/ComparingExpressionNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import ssg.data.DataView

import java.math.BigDecimal

import scala.util.boundary
import scala.util.boundary.break

/** Base class for comparison expression nodes. */
abstract class ComparingExpressionNode(
  protected val lhs:    LNode,
  protected val rhs:    LNode,
  private val relative: Boolean
) extends LValue
    with LNode {

  override def render(context: TemplateContext): DataView = boundary {
    var a: DataView = lhs.render(context)
    var b: DataView = rhs.render(context)

    if (LValue.isTemporal(a)) {
      a = DataView.from(LValue.asRubyDate(a, context))
    }
    if (LValue.isTemporal(b)) {
      b = DataView.from(LValue.asRubyDate(b, context))
    }
    if (!a.isNull && a.view.isInstanceOf[Number]) {
      a = DataView.from(asStrictNumber(a))
    }
    if (!b.isNull && b.view.isInstanceOf[Number]) {
      b = DataView.from(asStrictNumber(b))
    }

    val strictTypedExpressions =
      if (context != null && context.parser != null) context.parser.strictTypedExpressions
      else true

    if (relative) {
      val common = relativeCompareCommonRules(a, b, strictTypedExpressions)
      if (common.isDefined) {
        break(DataView.from(common.get))
      }
      if (!strictTypedExpressions) {
        if (!a.isNull && a.view.isInstanceOf[Boolean]) {
          a = DataView.from(booleanToNumber(a.view.asInstanceOf[Boolean]))
        }
        if (!b.isNull && b.view.isInstanceOf[Boolean]) {
          b = DataView.from(booleanToNumber(b.view.asInstanceOf[Boolean]))
        }
        if (a.isNull) {
          a = DataView.from(BigDecimal.ZERO)
        }
        if (b.isNull) {
          b = DataView.from(BigDecimal.ZERO)
        }
      }
    }
    if (!strictTypedExpressions) {
      if ((!a.isNull && a.view.isInstanceOf[Number] && canBeNumber(b)) || (!b.isNull && b.view.isInstanceOf[Number] && canBeNumber(a))) {
        a = DataView.from(asStrictNumber(a))
        b = DataView.from(asStrictNumber(b))
      }
    }
    doCompare(a, b, strictTypedExpressions)
  }

  private def booleanToNumber(a: Boolean): BigDecimal =
    if (a) BigDecimal.ONE else BigDecimal.ZERO

  protected def doCompare(a: DataView, b: DataView, strictTypedExpressions: Boolean): DataView

  protected def relativeCompareCommonRules(a: DataView, b: DataView, strictTypedExpressions: Boolean): Option[Boolean] =
    if (strictTypedExpressions) {
      if ((!a.isNull && a.view.isInstanceOf[Boolean]) || (!b.isNull && b.view.isInstanceOf[Boolean])) {
        Some(false)
      } else if (a.isNull || b.isNull) {
        Some(false)
      } else {
        None
      }
    } else {
      None
    }
}
