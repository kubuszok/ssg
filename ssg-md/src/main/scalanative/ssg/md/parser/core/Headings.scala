/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: an ATX heading's opening marker. Two of flexmark's three patterns compile here; the
 * third, `^#{1,6}(?:[ \t]*(?=[^ \t#])|[ \t]+$)` (no empty heading without a space), ends in a
 * lookahead RE2 lacks and `Regexes` answers with null, so it is walked here as java's engine walks
 * it: one to six `#`, the spacing after them, and then either a character that is not a space, a tab
 * or a `#`, or the end of the line after at least one space. Backtracking cannot rescue a failed
 * walk — a shorter run of `#` or of spacing is always followed by a `#` or a space. */
package ssg
package md
package parser
package core

import java.util.regex.Pattern

object Headings {

  /** the length of the opening marker (the `#`s and the spacing the pattern takes) at the start of the sequence, or -1 */
  def atx(atxHeading: Pattern, trySequence: CharSequence): Int =
    if (atxHeading != null) { // java interop boundary: null is the refused pattern, see above
      val matcher = atxHeading.matcher(trySequence)
      if (matcher.find()) matcher.end() else -1
    } else {
      val len    = trySequence.length
      var hashes = 0
      while (hashes < len && trySequence.charAt(hashes) == '#') hashes += 1
      if (hashes < 1 || hashes > 6) {
        -1
      } else {
        var end = hashes
        while (end < len && (trySequence.charAt(end) == ' ' || trySequence.charAt(end) == '\t')) end += 1
        val followedByText = end < len && trySequence.charAt(end) != '#'
        val spacedToEnd    = end == len && end > hashes
        if (followedByText || spacedToEnd) end else -1
      }
    }
}
