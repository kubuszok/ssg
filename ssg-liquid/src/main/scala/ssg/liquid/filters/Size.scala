/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Size.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

import ssg.liquid.parser.Inspectable

/** Liquid "size" filter — return the size of an array or string. */
class Size extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    var v: Any = value
    v match {
      case insp: Inspectable =>
        val evaluated = context.parser.evaluate(insp)
        v = evaluated.toLiquid()
      case _ => // no-op
    }
    if (isMap(v)) {
      asMap(v).size()
    } else if (isArray(v)) {
      asArray(v, context).length
    } else if (isString(v)) {
      asString(v, context).length()
    } else if (isNumber(v)) {
      // we're only using 64 bit longs, no BigIntegers or the like.
      // So just return 8 (the number of bytes in a long).
      8
    } else {
      // boolean or nil
      0
    }
  }
}
