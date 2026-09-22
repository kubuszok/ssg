/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: a list item marker at the start of the sequence. flexmark's pattern ends in a
 * lookahead — the marker must be followed by a space or tab, or by nothing when the option allows —
 * which RE2 lacks; the marker is matched without it and the character after the match is checked
 * here. The matcher's groups and end are then exactly java's. */
package ssg
package md
package parser
package core

import java.util.regex.{ Matcher, Pattern }

object ListMarkers {

  private val patterns: java.util.HashMap[String, Pattern] = new java.util.HashMap()

  /** the matcher positioned on the marker, or null when there is none — java's own `find()` contract */
  def find(parsing: ssg.md.ast.util.Parsing, rest: CharSequence): Matcher = {
    // java quotes the prefix characters with `\Q…\E` inside the class, which RE2 does not accept there
    val prefix = ssg.md.util.sequence.RegexCompat.charClassEscape(parsing.itemPrefixChars)
    val regex  =
      if (parsing.listsOrderedItemDotOnly) "^([" + prefix + "])|^(\\d{1,9})([.])"
      else "^([" + prefix + "])|^(\\d{1,9})([.)])"
    var pattern = patterns.get(regex)
    if (pattern == null) { // java interop boundary: an absent key is null
      pattern = Pattern.compile(regex)
      patterns.put(regex, pattern)
    }
    val matcher = pattern.matcher(rest)
    if (!matcher.find()) {
      null
    } else {
      val end      = matcher.end()
      val followed = end < rest.length && (rest.charAt(end) == ' ' || rest.charAt(end) == '\t')
      val allowed  = followed || (!parsing.listsItemMarkerSpace && end == rest.length)
      if (allowed) matcher else null
    }
  }
}
