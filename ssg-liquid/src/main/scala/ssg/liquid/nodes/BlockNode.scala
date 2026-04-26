/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/BlockNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *   Idiom: for-each with null check, static imports → companion object calls
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/nodes/BlockNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package nodes

import java.util.{ ArrayList, List => JList }

import scala.util.boundary
import scala.util.boundary.break

class BlockNode extends LNode {

  private val children: ArrayList[LNode] = new ArrayList[LNode]()

  def add(node: LNode): Unit =
    children.add(node)

  def getChildren: JList[LNode] = new ArrayList[LNode](children)

  override def render(context: TemplateContext): Any = boundary {
    val builder = context.newObjectAppender(children.size())
    var i       = 0
    while (i < children.size()) {
      val node = children.get(i)
      i += 1

      // Since tags can be "empty", node can be null, in which case we simply continue.
      // For example, the tag {% # inline comment %} is considered "empty".
      if (node != null) {
        val value = node.render(context)
        if (value != null) {
          if ((value.asInstanceOf[AnyRef] eq LValue.BREAK) || (value.asInstanceOf[AnyRef] eq LValue.CONTINUE)) {
            break(value)
          } else {
            value match {
              case list: JList[?] =>
                val it = list.iterator()
                while (it.hasNext)
                  builder.append(postprocess(it.next(), context))
              case arr: Array[?] =>
                var j = 0
                while (j < arr.length) {
                  builder.append(postprocess(arr(j), context))
                  j += 1
                }
              case _ =>
                builder.append(postprocess(value, context))
            }
          }
        }
      }
    }
    builder.getResult
  }

  private def postprocess(value: Any, context: TemplateContext): Any =
    if (LValue.isTemporal(value)) {
      val time = LValue.asRubyDate(value, context)
      LValue.rubyDateTimeFormat.format(time)
    } else {
      value
    }
}
