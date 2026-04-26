/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/OutputNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *   Idiom: Nullable Integer → Int with -1 sentinel
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/nodes/OutputNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package nodes

import ssg.liquid.exceptions.LiquidException

import java.math.BigDecimal
import java.util.{ ArrayList, List => JList }

class OutputNode(
  private val expression:       LNode,
  private val unparsed:         String,
  private var unparsedLine:     Int,
  private var unparsedPosition: Int
) extends LNode {

  private val filters: JList[FilterNode] = new ArrayList[FilterNode]()

  def addFilter(filter: FilterNode): Unit =
    filters.add(filter)

  override def render(context: TemplateContext): Any = {
    var value: Any = expression.render(context)

    var i = 0
    while (i < filters.size()) {
      value = filters.get(i).apply(value, context)
      i += 1
    }

    if (context != null && context.parser.errorMode == TemplateParser.ErrorMode.WARN) {
      val localUnparsed = unparsed
      if (!LValue.isBlank(localUnparsed)) {
        val truncated = if (localUnparsed.length() > 30) localUnparsed.substring(0, 30) + "..." else localUnparsed
        if (unparsedLine < 0) unparsedLine = -1
        if (unparsedPosition < 0) unparsedPosition = -1
        context.addError(new LiquidException("unexpected output: " + truncated, unparsedLine, unparsedPosition, null))
      }
    }

    value match {
      case bd: BigDecimal if !bd.isInstanceOf[PlainBigDecimal] =>
        PlainBigDecimal(bd.toString)
      case _ =>
        value
    }
  }
}
