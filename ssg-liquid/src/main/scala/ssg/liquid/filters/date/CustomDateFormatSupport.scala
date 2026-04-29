/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/date/CustomDateFormatSupport.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.date → ssg.liquid.filters.date
 *   Convention: Java interface → Scala trait
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/date/CustomDateFormatSupport.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters
package date

import java.time.ZonedDateTime

/** Extension point for custom date types.
  *
  * Implement this trait to register custom date/time types with the Liquid date filter. The `support` method checks if a value is of the custom type, and `getValue` converts it to a ZonedDateTime.
  */
trait CustomDateFormatSupport[T] {

  /** Converts a value of this custom date type to ZonedDateTime. */
  def getValue(value: T): ZonedDateTime

  /** Checks whether the given value is a supported custom date type. */
  def support(in: Any): Boolean
}
