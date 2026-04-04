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
 */
package ssg
package liquid
package nodes

import java.math.BigDecimal

import scala.util.boundary
import scala.util.boundary.break

/** Base class for comparison expression nodes.
  *
  * Expressions are two kinds:
  *   - relative (>, >=, <, <=) — different types cannot be compared
  *   - equality (==, <>, !=) — looser rules
  *
  * Ruby is strictly typed for comparisons, so comparing string '98' and number 97 will raise an exception in strict mode.
  */
abstract class ComparingExpressionNode(
  protected val lhs:    LNode,
  protected val rhs:    LNode,
  private val relative: Boolean
) extends LValue
    with LNode {

  override def render(context: TemplateContext): Any = boundary {
    var a: Any = lhs.render(context)
    var b: Any = rhs.render(context)

    if (LValue.isTemporal(a)) {
      a = LValue.asRubyDate(a, context)
    }
    if (LValue.isTemporal(b)) {
      b = LValue.asRubyDate(b, context)
    }
    if (a.isInstanceOf[Number]) {
      a = asStrictNumber(a.asInstanceOf[Number])
    }
    if (b.isInstanceOf[Number]) {
      b = asStrictNumber(b.asInstanceOf[Number])
    }

    val strictTypedExpressions =
      if (context != null && context.parser != null) context.parser.strictTypedExpressions
      else true

    if (relative) {
      val common = relativeCompareCommonRules(a, b, strictTypedExpressions)
      if (common.isDefined) {
        break(common.get)
      }
      if (!strictTypedExpressions) {
        if (a.isInstanceOf[Boolean]) {
          a = booleanToNumber(a.asInstanceOf[Boolean])
        }
        if (b.isInstanceOf[Boolean]) {
          b = booleanToNumber(b.asInstanceOf[Boolean])
        }
        if (a == null) {
          a = BigDecimal.ZERO
        }
        if (b == null) {
          b = BigDecimal.ZERO
        }
      }
    }
    if (!strictTypedExpressions) {
      if ((a.isInstanceOf[Number] && canBeNumber(b)) || (b.isInstanceOf[Number] && canBeNumber(a))) {
        a = asStrictNumber(a)
        b = asStrictNumber(b)
      }
    }
    doCompare(a, b, strictTypedExpressions)
  }

  private def booleanToNumber(a: Boolean): BigDecimal =
    if (a) BigDecimal.ONE else BigDecimal.ZERO

  protected def doCompare(a: Any, b: Any, strictTypedExpressions: Boolean): Any

  protected def relativeCompareCommonRules(a: Any, b: Any, strictTypedExpressions: Boolean): Option[Any] =
    if (strictTypedExpressions) {
      if (a.isInstanceOf[Boolean] || b.isInstanceOf[Boolean]) {
        // relative comparing with boolean should be false all the time
        Some(false)
      } else if (a == null || b == null) {
        // relative comparing with null should be false all the time
        Some(false)
      } else {
        None
      }
    } else {
      None
    }
}
