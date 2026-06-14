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
 * Covenant-java-reference: src/main/java/liqp/nodes/OutputNode.java
 * Covenant-verified: 2026-06-14
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import ssg.data.DataView
import ssg.liquid.exceptions.LiquidException

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

  override def render(context: TemplateContext): DataView = {
    var value: DataView = expression.render(context)

    var i = 0
    while (i < filters.size()) {
      value = filters.get(i).apply(value, context)
      i += 1
    }

    // LiquidParser.g4:229-233: the output rule has three alternatives gated by
    // errorMode predicates. Under STRICT (g4:231 `{isStrict()}? outStart term filter* OutEnd`)
    // there is NO `unparsed` alternative, so any trailing content fails the rule, the ANTLR
    // listener fires and Template.java:91-98 throws unconditionally. Under WARN/LAX
    // (g4:232 `{isWarn() || isLax()}? outStart term filter* unparsed=not_out_end? OutEnd`)
    // the trailing content is captured as `unparsed`: WARN records an "unexpected output"
    // error (GtNodeTest:113) and still renders; LAX renders silently.
    if (context != null) {
      val localUnparsed = unparsed
      if (!LValue.isBlank(localUnparsed)) {
        val truncated = if (localUnparsed.length() > 30) localUnparsed.substring(0, 30) + "..." else localUnparsed
        if (unparsedLine < 0) unparsedLine = -1
        if (unparsedPosition < 0) unparsedPosition = -1
        context.parser.errorMode match {
          case TemplateParser.ErrorMode.STRICT =>
            throw new LiquidException("unexpected output: " + truncated, unparsedLine, unparsedPosition, null)
          case TemplateParser.ErrorMode.WARN =>
            context.addError(new LiquidException("unexpected output: " + truncated, unparsedLine, unparsedPosition, null))
          case TemplateParser.ErrorMode.LAX =>
            ()
        }
      }
    }

    // Ensure BigDecimals get wrapped as PlainBigDecimal
    if (!value.isNull) {
      value.view match {
        case bd: java.math.BigDecimal if !bd.isInstanceOf[PlainBigDecimal] =>
          DataView.from(PlainBigDecimal(bd.toString))
        case _ =>
          value
      }
    } else {
      value
    }
  }
}
