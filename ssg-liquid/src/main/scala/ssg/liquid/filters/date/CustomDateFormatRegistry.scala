/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/date/CustomDateFormatRegistry.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.date → ssg.liquid.filters.date
 *   Convention: Java static class → Scala object
 *   Idiom: Uses ArrayList for thread-unsafe mutable storage (matches original)
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/date/CustomDateFormatRegistry.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters
package date

import java.time.ZonedDateTime
import java.util.ArrayList

import scala.util.boundary
import scala.util.boundary.break

/** Global registry of custom date/time type support.
  *
  * Known types are stored in a static (object-level) list. Subtypes added later take precedence (prepended to list).
  */
object CustomDateFormatRegistry {

  // might be better storage for this will be tree,
  // so the subtypes will be properly handled
  // and parent type will not override child's one
  private val supportedTypes: ArrayList[CustomDateFormatSupport[Any]] = new ArrayList()

  @SuppressWarnings(Array("unchecked"))
  def add(supportThis: CustomDateFormatSupport[?]): Unit =
    supportedTypes.add(0, supportThis.asInstanceOf[CustomDateFormatSupport[Any]])

  def isRegistered(typeSupport: CustomDateFormatSupport[?]): Boolean =
    supportedTypes.contains(typeSupport)

  def isCustomDateType(value: Any): Boolean = boundary {
    var i = 0
    while (i < supportedTypes.size()) {
      if (supportedTypes.get(i).support(value)) break(true)
      i += 1
    }
    false
  }

  def getFromCustomType(value: Any): ZonedDateTime = boundary {
    var i = 0
    while (i < supportedTypes.size()) {
      val el = supportedTypes.get(i)
      if (el.support(value)) break(el.getValue(value))
      i += 1
    }
    throw new UnsupportedOperationException()
  }
}
