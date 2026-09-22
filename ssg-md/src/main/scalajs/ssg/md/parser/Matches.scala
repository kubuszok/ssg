/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js: how the inline parser matches a pattern at its index — java's own region-and-find. Scala
 * Native compiles some of flexmark's patterns without a lookahead and gives one character back at
 * the match; this row has nothing to give back. */
package ssg
package md
package parser

import java.util.regex.Pattern

object Matches {

  /** the match's start and end at or after `index`, or null when there is none */
  def find(re: Pattern, input: CharSequence, index: Int): Array[Int] = {
    val matcher = re.matcher(input)
    matcher.region(index, input.length)
    if (matcher.find()) Array(matcher.start(), matcher.end()) else null
  }
}
