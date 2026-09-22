/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: an ATX heading's opening marker, found with whichever of flexmark's three patterns the options
 * chose. Scala Native's RE2 refuses the one with a lookahead and walks it in code at the same name. */
package ssg
package md
package parser
package core

import java.util.regex.Pattern

object Headings {

  /** the length of the opening marker (the `#`s and the spacing the pattern takes) at the start of the sequence, or -1 */
  def atx(atxHeading: Pattern, trySequence: CharSequence): Int = {
    val matcher = atxHeading.matcher(trySequence)
    if (matcher.find()) matcher.end() else -1
  }
}
