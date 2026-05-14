/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/Include.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/tags/Include.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package tags

import ssg.data.DataView
import ssg.liquid.antlr.NameResolver
import ssg.liquid.nodes.LNode

import java.util.HashMap

import scala.collection.immutable.VectorMap

class Include(_name: String) extends Tag(_name) {

  def this() =
    this("include")

  override def render(context: TemplateContext, nodes: Array[LNode]): DataView =
    try {
      val includeResource = asString(nodes(0).render(context), context)

      val source   = detectSource(context, includeResource)
      val template = context.parser.parse(source.content)

      val variables = new HashMap[String, DataView]()

      if (nodes.length > 1) {
        if (context.parser.liquidStyleInclude) {
          val value = nodes(1).render(context)
          context.put(includeResource, value)
        } else {
          var includeMap = VectorMap.empty[String, DataView]
          var i          = 1
          while (i < nodes.length) {
            val rendered = nodes(i).render(context)
            if (isMap(rendered)) {
              val map = asMap(rendered)
              map.foreach { case (k, v) => includeMap = includeMap.updated(k, v) }
            } else {
              throw new ssg.liquid.exceptions.LiquidException("Include parameter must be a key=value pair, got: " + rendered)
            }
            i += 1
          }
          variables.put("include", DataView.from(includeMap))
        }
      }

      template.renderToObjectUnguarded(variables, context, true)
    } catch {
      case e: Exception =>
        if (context.parser.showExceptionsFromInclude) {
          throw new RuntimeException("problem with evaluating include", e)
        } else {
          DataView.from("")
        }
    }

  protected def detectSource(context: TemplateContext, includeResource: String): NameResolver.ResolvedSource =
    context.parser.nameResolver.resolve(includeResource)
}
