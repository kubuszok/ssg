/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: flexmark's user-mention pattern `^(@)([a-z\d](?:[a-z\d]|-(?=[a-z\d])){0,38})\b`
 * (case-insensitive) uses a lookahead RE2 does not have, so its language is matched here in code,
 * exactly as java's engine walks it: a `@`, a letter or digit, then at most 38 more characters each
 * a letter or digit or a `-` whose next character is a letter or digit, taken greedily; then the
 * longest such prefix after which a word boundary holds. The groups and the parser's new index are
 * what `LightInlineParser.matchWithGroups` answers on the JVM. */
package ssg
package md
package ext
package gfm
package users
package internal

import ssg.md.parser.LightInlineParser
import ssg.md.util.sequence.BasedSequence

object GitHubUsers {

  private def isNameChar(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')

  /** `\w` as java's pattern reads it without UNICODE_CHARACTER_CLASS: ASCII letters, digits and `_` */
  private def isWord(c: Char): Boolean = isNameChar(c) || c == '_'

  def matchWithGroups(inlineParser: LightInlineParser): Array[BasedSequence] = {
    val input = inlineParser.getInput()
    val start = inlineParser.getIndex()
    val len   = input.length()
    if (start >= len) {
      null // java interop boundary: matchWithGroups answers null when the input is exhausted
    } else if (input.charAt(start) != '@' || start + 1 >= len || !isNameChar(input.charAt(start + 1))) {
      null
    } else {
      // the greedy walk of `(?:[a-z\d]|-(?=[a-z\d])){0,38}`
      var end   = start + 2
      var count = 0
      var more  = true
      while (more && count < 38 && end < len) {
        val c = input.charAt(end)
        if (isNameChar(c)) { end += 1; count += 1 }
        else if (c == '-' && end + 1 < len && isNameChar(input.charAt(end + 1))) { end += 1; count += 1 }
        else more = false
      }
      // backtracking over the repetition until `\b` holds; the first character of the name is mandatory
      def boundaryAt(i: Int): Boolean = isWord(input.charAt(i - 1)) != (i < len && isWord(input.charAt(i)))
      while (end > start + 2 && !boundaryAt(end)) end -= 1
      if (!boundaryAt(end)) {
        null
      } else {
        inlineParser.setIndex(end)
        Array(input.subSequence(start, end), input.subSequence(start, start + 1), input.subSequence(start + 1, end))
      }
    }
  }
}
