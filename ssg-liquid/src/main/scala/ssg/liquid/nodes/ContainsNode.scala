/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/ContainsNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *   Idiom: Java arrays/lists → Scala collections for internal conversion
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/ContainsNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import ssg.data.DataView

class ContainsNode(private val lhs: LNode, private val rhs: LNode) extends LValue with LNode {

  override def render(context: TemplateContext): DataView = {
    var collection: DataView = lhs.render(context)
    val needle:     DataView = rhs.render(context)

    // If collection is a map, use its keys
    if (isMap(collection)) {
      val map = asMap(collection)
      collection = DataView.from(map.keys.toVector.map(DataView.from(_)))
    }

    if (isArray(collection)) {
      val array           = asArray(collection, context)
      val finalCollection = array.map(toSingleNumberType)
      val needleNorm      = toSingleNumberType(needle)
      DataView.from(finalCollection.exists(dv => dvEquals(dv, needleNorm)))
    } else if (isString(collection)) {
      DataView.from(asString(collection, context).contains(asString(needle, context)))
    } else {
      DataView.from(false)
    }
  }

  private def dvEquals(a: DataView, b: DataView): Boolean =
    if (a.isNull && b.isNull) true
    else if (a.isNull || b.isNull) false
    else a.view == b.view

  private def toSingleNumberType(dv: DataView): DataView =
    if (dv.isNull) dv
    else
      dv.view match {
        case _: (Short | Int | Long | Float | Double | java.math.BigDecimal) =>
          val n = dv.view.asInstanceOf[Number]
          DataView.from(LValue.asFormattedNumber(PlainBigDecimal(n.toString)))
        case _ => dv
      }
}
