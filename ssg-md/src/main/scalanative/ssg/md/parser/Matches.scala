/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: how the inline parser matches a pattern at its index — java's region-and-find, and
 * then the one thing `Regexes` could not put into the pattern: a link destination that may hold
 * spaces stops before a space that is followed by a quote (the title's opening), which java's
 * lookahead decided and RE2 cannot; the pattern took that space, and it is given back here. */
package ssg
package md
package parser

import java.util.regex.Pattern

object Matches {

  /** the match's start and end at or after `index`, or null when there is none */
  def find(re: Pattern, input: CharSequence, index: Int): Array[Int] = {
    val matcher = re.matcher(input)
    matcher.region(index, input.length)
    if (!matcher.find()) {
      null
    } else {
      val start = matcher.start()
      var end   = matcher.end()
      if (ssg.md.util.sequence.Regexes.givesBackSpaceBeforeQuote(re) && end > start && input.charAt(end - 1) == ' ' && end < input.length && (input.charAt(end) == '"' || input.charAt(end) == '\'')) {
        end -= 1
      }
      Array(start, end)
    }
  }
}
