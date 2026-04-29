/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/Strings.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util

object Strings {

  def repeat(s: String, count: Int): String = {
    val sb = new StringBuilder(s.length * count)
    var i  = 0
    while (i < count) {
      sb.append(s)
      i += 1
    }
    sb.toString()
  }
}
