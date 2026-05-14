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
 *   Idiom: Default adapters for DataView and VectorMap registered at init
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/where/PropertyResolverHelper.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters
package where

import ssg.data.DataView

import java.util.ArrayList

import scala.collection.immutable.VectorMap
import scala.util.boundary
import scala.util.boundary.break

/** Holds the chain of property resolver adapters. */
class PropertyResolverHelper {
  private val propertyResolverAdapters: ArrayList[PropertyResolverAdapter] = new ArrayList()

  def add(one: PropertyResolverAdapter): Unit =
    propertyResolverAdapters.add(one)

  def findFor(target: DataView): PropertyResolverAdapter = boundary {
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

    // Default resolver for DataView maps
    helper.add(
      new PropertyResolverAdapter {
        private val lValue:                                                                          LValue   = new LValue {}
        override def getItemProperty(context: TemplateContext, input: DataView, property: DataView): DataView = {
          val key = lValue.asString(property, context)
          input.asMap.fold(DataView.nil) { m =>
            m.get(key) match {
              case Some(inner) => inner
              case None        => DataView.nil
            }
          }
        }
        override def support(target: DataView): Boolean =
          !target.isNull && target.view.isInstanceOf[VectorMap[?, ?]]
      }
    )

    helper
  }
}
