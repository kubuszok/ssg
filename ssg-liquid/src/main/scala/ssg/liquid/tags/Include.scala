/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/Include.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 *   Convention: Uses NameResolver + Template.parse for template inclusion
 */
package ssg
package liquid
package tags

import ssg.liquid.nodes.LNode

import java.util.{ HashMap, Map => JMap }

/** Liquid include tag — includes external template files.
  *
  * Supports both Liquid-style and Jekyll-style include syntax:
  *   - Liquid: `{% include 'header' with product %}`
  *   - Jekyll: `{% include header.html title=page.title %}`
  */
class Include(_name: String) extends Tag(_name) {

  def this() = {
    this("include")
  }

  override def render(context: TemplateContext, nodes: Array[LNode]): Any =
    try {
      val includeResource = asString(nodes(0).render(context), context)

      // Resolve the template source via NameResolver
      val source = context.parser.nameResolver.resolve(includeResource)

      // Parse the included template
      val template = context.parser.parse(source.content)

      // Build variables for the included template
      val variables = new HashMap[String, Any]()

      if (nodes.length > 1) {
        if (context.parser.liquidStyleInclude) {
          // Liquid style: {% include 'template' with expression %}
          val value = nodes(1).render(context)
          context.put(includeResource, value)
        } else {
          // Jekyll style: {% include file var=val var=val %}
          val includeMap = new HashMap[String, Any]()
          variables.put("include", includeMap)
          var i = 1
          while (i < nodes.length) {
            val rendered = nodes(i).render(context)
            rendered match {
              case map: JMap[?, ?] =>
                includeMap.putAll(map.asInstanceOf[JMap[String, Any]])
              case _ =>
              // Non-map parameter — add as positional
            }
            i += 1
          }
        }
      }

      // Render the included template with the parent context
      template.renderToObjectUnguarded(variables, context, true)
    } catch {
      case e: Exception =>
        if (context.parser.showExceptionsFromInclude) {
          throw new RuntimeException("problem with evaluating include", e)
        } else {
          ""
        }
    }
}
