/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Last.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Last.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

/** Liquid "last" filter — get the last element of an array. */
class Last extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val array = asArray(value, context)
    if (array.length == 0) null else array(array.length - 1)
  }
}
