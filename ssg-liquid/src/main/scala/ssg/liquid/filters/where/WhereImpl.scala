/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/where/WhereImpl.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.where → ssg.liquid.filters.where
 *   Convention: Abstract base for where filter implementations
 */
package ssg
package liquid
package filters
package where

/** Abstract base for where filter implementations.
  *
  * Subclasses implement Jekyll-style or Liquid-style where semantics.
  */
abstract class WhereImpl(
  protected val context:        TemplateContext,
  protected val resolverHelper: PropertyResolverHelper
) extends LValue {

  def apply(value: Any, params: Array[Any]): Any
}
