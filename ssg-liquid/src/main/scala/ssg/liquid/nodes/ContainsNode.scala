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

import ssg.liquid.parser.{ Inspectable, LiquidSupport }

import java.util.{ ArrayList, Arrays, List => JList }

class ContainsNode(private val lhs: LNode, private val rhs: LNode) extends LValue with LNode {

  override def render(context: TemplateContext): Any = {
    var collection: Any = lhs.render(context)
    var needle:     Any = rhs.render(context)

    if (collection.isInstanceOf[Inspectable]) {
      val evaluated: LiquidSupport = context.parser.evaluate(collection)
      collection = evaluated.toLiquid()
    }

    if (isMap(collection)) {
      collection = asMap(collection).keySet().toArray
    }

    if (isArray(collection)) {
      val array           = asArray(collection, context)
      val finalCollection = toSingleNumberType(Arrays.asList(array*))
      needle = toSingleNumberType(needle)
      finalCollection.contains(needle)
    } else if (isString(collection)) {
      asString(collection, context).contains(asString(needle, context))
    } else {
      false
    }
  }

  private def toSingleNumberType(needle: Any): Any =
    needle match {
      case n: Number => LValue.asFormattedNumber(PlainBigDecimal(n.toString))
      case other => other
    }

  private def toSingleNumberType(asList: JList[?]): JList[Any] = {
    val res = new ArrayList[Any](asList.size())
    val it  = asList.iterator()
    while (it.hasNext) {
      val item = it.next()
      item match {
        case n: Number => res.add(LValue.asFormattedNumber(PlainBigDecimal(n.toString)))
        case other => res.add(other)
      }
    }
    res
  }
}
