/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/Template.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: Uses hand-written lexer/parser instead of ANTLR
 *   Idiom: Simplified API — parse returns Template, render returns String
 */
package ssg
package liquid

import ssg.liquid.nodes.BlockNode

import java.util.{ HashMap, LinkedHashMap, Map => JMap }

/** A parsed Liquid template, ready for rendering with variables.
  *
  * Created via `TemplateParser.parse()` or `Template.parse()`.
  */
final class Template(
  val templateParser: TemplateParser,
  private val root:   BlockNode,
  val templateSize:   Long = 0L
) {

  /** Renders this template with the given variables and returns the result as a String. */
  def render(variables: JMap[String, Any]): String =
    String.valueOf(renderToObject(variables))

  /** Renders this template with no variables. */
  def render(): String =
    render(new HashMap[String, Any]())

  /** Renders this template and returns the raw result object. */
  def renderToObject(variables: JMap[String, Any]): Any = {
    if (templateSize > templateParser.limitMaxTemplateSizeBytes) {
      throw new RuntimeException(s"template exceeds the max of ${templateParser.limitMaxTemplateSizeBytes} bytes")
    }

    val evaluatedVars: JMap[String, Any] = templateParser.evaluateMode match {
      case TemplateParser.EvaluateMode.EAGER =>
        // EAGER: convert all values eagerly via evaluate
        // Full EAGER support requires LiquidSupport.objectToMap (ISS-015);
        // for now, makes a defensive copy so mutations don't leak to caller
        new LinkedHashMap[String, Any](variables)
      case _ =>
        // LAZY: pass variables through as-is (converted on demand during rendering)
        variables
    }

    val context = new TemplateContext(templateParser, evaluatedVars)
    root.render(context)
  }

  /** Renders with an existing parent context (used by include tags). */
  def renderToObjectUnguarded(variables: JMap[String, Any], parentContext: TemplateContext, isInclude: Boolean): Any = {
    val context = if (isInclude) {
      val child = parentContext.newChildContext(new LinkedHashMap[String, Any](variables))
      child
    } else {
      new TemplateContext(templateParser, new LinkedHashMap[String, Any](variables))
    }
    root.render(context)
  }
}

object Template {

  /** Parses a Liquid template string with the default parser. */
  def parse(input: String): Template =
    TemplateParser.DEFAULT.parse(input)
}
