/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/where/PropertyResolverHelper.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.where → ssg.liquid.filters.where
 *   Convention: Java static singleton → Scala object
 *   Idiom: Default adapters for Inspectable and Map registered at init
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/where/PropertyResolverHelper.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters
package where

import ssg.liquid.parser.Inspectable

import java.util.{ ArrayList, Map => JMap }

import scala.util.boundary
import scala.util.boundary.break

/** Holds the chain of property resolver adapters.
  *
  * Default resolvers handle `Inspectable` (via `TemplateParser.evaluate`) and `Map` (direct lookup).
  */
class PropertyResolverHelper {
  private val propertyResolverAdapters: ArrayList[PropertyResolverAdapter] = new ArrayList()

  def add(one: PropertyResolverAdapter): Unit =
    propertyResolverAdapters.add(one)

  def findFor(target: Any): PropertyResolverAdapter = boundary {
    var i = 0
    while (i < propertyResolverAdapters.size()) {
      val e = propertyResolverAdapters.get(i)
      if (e.support(target)) break(e)
      i += 1
    }
    null
  }
}

object PropertyResolverHelper {

  val INSTANCE: PropertyResolverHelper = {
    val helper = new PropertyResolverHelper()

    // default resolver for Inspectable type
    // allow Inspectable items to be inspected via "where" filter
    helper.add(
      new PropertyResolverAdapter {
        // dummy LValue for accessing helper method #asString
        private val lValue:                                                                LValue = new LValue {}
        override def getItemProperty(context: TemplateContext, input: Any, property: Any): Any    = {
          val evaluated = context.parser.evaluate(input)
          evaluated.toLiquid().get(lValue.asString(property, context))
        }
        override def support(target: Any): Boolean =
          target.isInstanceOf[Inspectable]
      }
    )

    helper.add(
      new PropertyResolverAdapter {
        override def getItemProperty(context: TemplateContext, input: Any, property: Any): Any =
          input.asInstanceOf[JMap[?, ?]].get(property)
        override def support(target: Any): Boolean =
          target.isInstanceOf[JMap[?, ?]]
      }
    )

    helper
  }
}
