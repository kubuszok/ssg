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
 * Covenant-java-reference: src/main/java/liqp/nodes/BlockNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import ssg.data.DataView

import java.time.temporal.TemporalAccessor
import java.util.{ ArrayList, List => JList }

import scala.util.boundary
import scala.util.boundary.break

class BlockNode extends LNode {

  private val children: ArrayList[LNode] = new ArrayList[LNode]()

  def add(node: LNode): Unit =
    children.add(node)

  def getChildren: JList[LNode] = new ArrayList[LNode](children)

  override def render(context: TemplateContext): DataView = boundary {
    val builder = context.newObjectAppender(children.size())
    var i       = 0
    while (i < children.size()) {
      val node = children.get(i)
      i += 1

      // Since tags can be "empty", node can be null, in which case we simply continue.
      // For example, the tag {% # inline comment %} is considered "empty".
      if (node != null) {
        val value = node.render(context)
        if (!value.isNull) {
          if ((value eq DataView.BREAK) || (value eq DataView.CONTINUE)) {
            break(value)
          } else {
            value.view match {
              case v: Vector[?] =>
                val vec = v.asInstanceOf[Vector[DataView]]
                vec.foreach(dv => builder.append(postprocess(dv, context)))
              case _ =>
                builder.append(postprocess(value, context))
            }
          }
        }
      }
    }
    DataView.from(builder.getResult.toString)
  }

  private def postprocess(value: DataView, context: TemplateContext): Any =
    if (!value.isNull && value.view.isInstanceOf[TemporalAccessor]) {
      val time = LValue.asRubyDate(value, context)
      LValue.rubyDateTimeFormat.format(time)
    } else {
      value.toString
    }
}
