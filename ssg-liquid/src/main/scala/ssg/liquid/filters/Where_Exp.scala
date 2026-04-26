/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Where_Exp.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Convention: Jekyll-specific filter. Uses Template.parse to evaluate
 *               the expression for each item.
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Where_Exp.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import ssg.liquid.parser.Inspectable

import java.util.ArrayList

/** Filters array elements using a Liquid expression (Jekyll-specific).
  *
  * Usage: `{{ items | where_exp: "item", "item.published == true" }}`
  */
class Where_Exp extends Filter("where_exp") {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    var items: Array[Any] = null

    if (isArray(value)) {
      items = asArray(value, context)
    }
    if (items == null && value.isInstanceOf[Inspectable]) {
      val evaluated = context.parser.evaluate(value)
      val liquid    = evaluated.toLiquid()
      if (isMap(liquid)) {
        items = asMap(liquid).values().toArray
      }
    }
    if (items == null && isMap(value)) {
      items = asMap(value).values().toArray
    }

    if (items == null) {
      value
    } else {
      val varName       = asString(params(0), context)
      val strExpression = asString(params(1), context)

      // Build a parser with evaluateInOutputTag=true to evaluate the expression
      val exprParser   = new TemplateParser.Builder(context.parser).withEvaluateInOutputTag(true).build()
      val exprTemplate = exprParser.parse("{{ " + strExpression + " }}")

      val result = new ArrayList[Any]()
      for (item <- items)
        if (matchCondition(context, item, varName, exprTemplate)) {
          result.add(item)
        }
      result
    }
  }

  private def matchCondition(context: TemplateContext, item: Any, varName: String, expression: Template): Boolean = {
    val vars = java.util.Collections.singletonMap[String, Any](varName, item)
    "true" == String.valueOf(expression.renderToObjectUnguarded(vars, context, false))
  }
}
