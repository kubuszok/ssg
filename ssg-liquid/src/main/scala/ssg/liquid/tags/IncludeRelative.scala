/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/IncludeRelative.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 *   Convention: Resolves includes relative to the current file's root folder
 */
package ssg
package liquid
package tags

import ssg.liquid.nodes.LNode

import java.util.{ HashMap, Map => JMap }

/** Jekyll-style include_relative tag.
  *
  * Resolves templates relative to the current file location (from context root folder), unlike the standard include tag which uses the configured NameResolver.
  */
class IncludeRelative extends Include("include_relative") {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any =
    try {
      val includeResource = asString(nodes(0).render(context), context)

      // Resolve via NameResolver — relative path resolution is delegated to
      // the configured resolver (e.g., LocalFSNameResolver on JVM).
      // The root folder from context can be used by custom resolvers.
      val source = context.parser.nameResolver.resolve(includeResource)

      val template  = context.parser.parse(source.content)
      val variables = new HashMap[String, Any]()

      if (nodes.length > 1) {
        val includeMap = new HashMap[String, Any]()
        variables.put("include", includeMap)
        var i = 1
        while (i < nodes.length) {
          val rendered = nodes(i).render(context)
          rendered match {
            case map: JMap[?, ?] =>
              includeMap.putAll(map.asInstanceOf[JMap[String, Any]])
            case _ =>
          }
          i += 1
        }
      }

      template.renderToObjectUnguarded(variables, context, true)
    } catch {
      case e: Exception =>
        if (context.parser.showExceptionsFromInclude) {
          throw new RuntimeException("problem with evaluating include_relative", e)
        } else {
          ""
        }
    }
}
