/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/where/PropertyResolverAdapter.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.where → ssg.liquid.filters.where
 *   Convention: Java interface → Scala trait
 */
package ssg
package liquid
package filters
package where

/** Used for resolving properties by name for specific kind of objects.
  *
  * Native implementation has equivalent ":to_liquid" and ":data" that used for resolving properties by name for objects that support such method. In java we do not stick to special interfaces of
  * Jekyll/Liquid that do not have equivalent/meaning here, but still provide a way to create alternative properties resolver for custom objects.
  *
  * See here sample implementation for ":to_liquid" via this interface here: https://gist.github.com/msangel/74c6cec96ea4a4ecc01187e465fdeb14
  *
  * See sample implementation for ":data" here: https://gist.github.com/msangel/4a9b4404b233a6ff57a4ca54db3bfc1f
  */
trait PropertyResolverAdapter {
  def getItemProperty(context: TemplateContext, input: Any, property: Any): Any
  def support(target: Any): Boolean
}
