/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Push.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Push.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import java.util.ArrayList

/** Liquid "push" filter — returns a new array with the given item(s) added to the end.
  *
  * Jekyll-specific filter for array manipulation.
  */
class Push extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (!isArray(value)) {
      value
    } else if (params.length == 0) {
      value
    } else {
      val valueList = asList(value, context)
      val paramList = asList(params, context)
      val list      = new ArrayList[Any](valueList.size() + paramList.size())
      list.addAll(valueList)
      list.addAll(paramList)
      list
    }
}
