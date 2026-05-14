/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Truncatewords.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Truncatewords.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

class Truncatewords extends Filter {

  /*
   * truncatewords(input, words = 15, truncate_string = "...")
   *
   * Truncate a string down to x words
   */
  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    if (value == null) {
      DataView.from("")
    } else {
      val text           = asString(value, context)
      val words          = text.split("\\s+")
      var length         = 15
      var truncateString = "..."

      if (params.length >= 1) {
        length = asNumber(get(0, params)).intValue()
      }

      if (params.length >= 2) {
        truncateString = asString(get(1, params), context)
      }

      if (length >= words.length) {
        DataView.from(text)
      } else {
        DataView.from(join(words, length) + truncateString)
      }
    }

  private def join(words: Array[String], count: Int): String = {
    val builder = new StringBuilder()
    var i       = 0
    while (i < count) {
      builder.append(words(i)).append(" ")
      i += 1
    }
    builder.toString().trim()
  }
}
