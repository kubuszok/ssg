/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Filter.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Convention: Abstract class extending LValue
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Filter.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import java.util.{ Arrays, Locale }

/** Base class for Liquid filters.
  *
  * A filter transforms a value (the left side of the pipe) and returns the result. Filters can take additional parameters.
  */
abstract class Filter(_name: String) extends LValue {

  /** Constructor that derives the name from the class's simple name, lowercased. */
  def this() =
    this(null)

  /** The name of this filter. */
  val name: String = if (_name != null) _name else getClass.getSimpleName.toLowerCase(Locale.ENGLISH)

  /** Applies the filter on the 'value', with the given 'context'. */
  def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = value

  /** Check the number of parameters and throw an exception if needed. */
  final def checkParams(params: Array[Any], expected: Int): Unit =
    if (params == null || params.length != expected) {
      val actual = if (params == null) 1 else params.length + 1
      throw new RuntimeException(s"Liquid error: wrong number of arguments (given $actual for ${expected + 1})")
    }

  final def checkParams(params: Array[Any], min: Int, max: Int): Unit =
    if (params == null || params.length < min || params.length > max) {
      val actual = if (params == null) 1 else params.length + 1
      throw new RuntimeException(s"Liquid error: wrong number of arguments (given $actual expected ${min + 1}..${max + 1})")
    }

  /** Returns a value at a specific index from an array of parameters. */
  protected def get(index: Int, params: Array[Any]): Any = {
    if (index >= params.length) {
      throw new RuntimeException(
        s"error in filter '$name': cannot get param index: $index from: ${Arrays.toString(params.asInstanceOf[Array[AnyRef]])}"
      )
    }
    params(index)
  }
}
