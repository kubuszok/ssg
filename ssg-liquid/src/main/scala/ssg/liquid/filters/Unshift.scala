/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Unshift.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Unshift.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import java.util.ArrayList

/** Liquid "unshift" filter — returns a new array with the given item(s) added to the beginning.
  *
  * Jekyll-specific filter for array manipulation.
  */
class Unshift extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else if (params.length == 0) {
      value
    } else {
      val valueList = asList(value, context)
      val paramList = asList(params, context)
      val list      = new ArrayList[Any](valueList.size() + paramList.size())
      list.addAll(paramList)
      list.addAll(valueList)
      list
    }
}
