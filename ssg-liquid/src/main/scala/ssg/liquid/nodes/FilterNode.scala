/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/FilterNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *   Convention: Removed ANTLR ParserRuleContext dependency — line/tokenStartIndex passed directly
 */
package ssg
package liquid
package nodes

import ssg.liquid.filters.Filter

import java.util.{ ArrayList, List => JList }

class FilterNode(
  private val line:            Int,
  private val tokenStartIndex: Int,
  private val filter:          Filter
) extends LNode {

  private val params: JList[LNode] = new ArrayList[LNode]()

  def add(param: LNode): Unit =
    params.add(param)

  /** Applies this filter to the given value. Called by OutputNode during rendering. */
  def apply(value: Any, context: TemplateContext): Any =
    try {
      val paramValues = new ArrayList[Any]()
      var i           = 0
      while (i < params.size()) {
        paramValues.add(params.get(i).render(context))
        i += 1
      }
      filter.apply(value, context, paramValues.toArray)
    } catch {
      case e: Exception =>
        throw new RuntimeException(s"error on line $line, index $tokenStartIndex: ${e.getMessage}", e)
    }

  override def render(context: TemplateContext): Any =
    throw new RuntimeException("cannot render a filter")
}

object FilterNode {

  def apply(line: Int, tokenStartIndex: Int, text: String, filter: Filter): FilterNode = {
    if (filter == null) {
      throw new IllegalArgumentException(s"error on line $line, index $tokenStartIndex: no filter available named: $text")
    }
    new FilterNode(line, tokenStartIndex, filter)
  }
}
