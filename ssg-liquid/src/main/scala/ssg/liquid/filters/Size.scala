/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Size.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Size.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

/** Liquid "size" filter — return the size of an array or string. */
class Size extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView = {
    val v: DataView = value
    if (isMap(v)) {
      DataView.from(asMap(v).size)
    } else if (isArray(v)) {
      DataView.from(asArray(v, context).size)
    } else if (isString(v)) {
      DataView.from(asString(v, context).length())
    } else if (isNumber(v)) {
      // we're only using 64 bit longs, no BigIntegers or the like.
      // So just return 8 (the number of bytes in a long).
      DataView.from(8)
    } else {
      // boolean or nil
      DataView.from(0)
    }
  }
}
