/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js: a fenced code block's opening and closing fence, found with flexmark's own patterns. Scala
 * Native's RE2 engine has no lookahead and restates both without it at the same name. */
package ssg
package md
package parser
package core

import java.util.regex.Pattern

object Fences {

  private val OPENING_FENCE: Pattern = Pattern.compile("^`{3,}(?!.*`)|^~{3,}(?!.*~)")
  private val CLOSING_FENCE: Pattern = Pattern.compile("^(?:`{3,}|~{3,})(?=[ \t]*$)")

  /** the length of an opening fence at the start of the sequence, or -1 */
  def openingLength(trySequence: CharSequence): Int = {
    val matcher = OPENING_FENCE.matcher(trySequence)
    if (matcher.find()) matcher.group(0).length else -1
  }

  /** the length of a closing fence at the start of the sequence, or -1 */
  def closingLength(trySequence: CharSequence): Int = {
    val matcher = CLOSING_FENCE.matcher(trySequence)
    if (matcher.find()) matcher.group(0).length else -1
  }
}
