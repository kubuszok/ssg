/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: `java.text.MessageFormat` does not exist here, so a message pattern is filled by replacing
 * each `{n}` with its argument — the subset flexmark's own option-parser messages use, and what the
 * hand-written port did. The JVM row calls `MessageFormat.format` itself. */
package ssg
package md
package util
package misc

object Messages {

  def format(pattern: String, params: Array[Object]): String = {
    var result = pattern
    var i      = 0
    while (i < params.length) {
      result = result.replace("{" + i + "}", String.valueOf(params(i)))
      i += 1
    }
    result
  }
}
