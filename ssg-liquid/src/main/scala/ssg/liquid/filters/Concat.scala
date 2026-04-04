/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Concat.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

import java.util.{ ArrayList, Arrays }

/** Liquid "concat" filter — concatenates two arrays. */
class Concat extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    checkParams(params, 1)
    if (!isArray(params(0))) {
      throw new RuntimeException("Liquid error: concat filter requires an array argument")
    }
    val allValues = new ArrayList[Any]()
    if (isArray(value)) {
      allValues.addAll(Arrays.asList(asArray(value, context)*))
    }
    allValues.addAll(Arrays.asList(asArray(params(0), context)*))
    allValues.toArray()
  }
}
