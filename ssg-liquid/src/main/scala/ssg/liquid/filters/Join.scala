/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Join.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

/** Liquid "join" filter — joins elements of an array with a separator. */
class Join extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (value == null) {
      ""
    } else {
      val array = asArray(value, context)
      if (array.length == 0) {
        ""
      } else {
        val builder = context.newObjectAppender(array.length)
        val glue    = if (params.length == 0) " " else asString(get(0, params), context)
        var i       = 0
        while (i < array.length) {
          builder.append(asAppendableObject(array(i), context))
          if (i < array.length - 1) {
            builder.append(glue)
          }
          i += 1
        }
        builder.getResult
      }
    }
}
