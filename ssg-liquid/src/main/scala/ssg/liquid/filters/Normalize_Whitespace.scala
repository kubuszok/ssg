/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Normalize_Whitespace.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Normalize_Whitespace.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import java.util.regex.Pattern

class Normalize_Whitespace extends Filter("normalize_whitespace") {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    if (value == null) {
      ""
    } else {
      val string = value.toString.trim()
      Normalize_Whitespace.pattern.matcher(string).replaceAll(" ")
    }
}

object Normalize_Whitespace {
  private val pattern: Pattern = Pattern.compile("\\s+")
}
