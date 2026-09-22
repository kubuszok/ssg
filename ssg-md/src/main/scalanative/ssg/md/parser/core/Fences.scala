/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: a fenced code block's opening and closing fence without the lookahead flexmark's
 * patterns use, which RE2 lacks. The fence is a capturing group and the rest of the line is
 * consumed, so the group's length is what java's whole match was; java's `.` and `$` stop at a line
 * terminator, restated as the explicit terminator set (as characters: RE2 has no `\uXXXX` escape). */
package ssg
package md
package parser
package core

import java.util.regex.Pattern

object Fences {

  private val EOL = "\n\r\u0085  "

  // java: ^`{3,}(?!.*`)|^~{3,}(?!.*~)
  private val OPENING_FENCE: Pattern = Pattern.compile("^(`{3,})[^`" + EOL + "]*(?:[" + EOL + "]|$)|^(~{3,})[^~" + EOL + "]*(?:[" + EOL + "]|$)")
  // java: ^(?:`{3,}|~{3,})(?=[ \t]*$)
  private val CLOSING_FENCE: Pattern = Pattern.compile("^(`{3,}|~{3,})[ \t]*(?:\r\n|[" + EOL + "])?$")

  /** the length of an opening fence at the start of the sequence, or -1 */
  def openingLength(trySequence: CharSequence): Int = {
    val matcher = OPENING_FENCE.matcher(trySequence)
    if (!matcher.find()) -1
    else if (matcher.group(1) != null) matcher.group(1).length // java interop boundary: an unmatched group is null
    else matcher.group(2).length
  }

  /** the length of a closing fence at the start of the sequence, or -1 */
  def closingLength(trySequence: CharSequence): Int = {
    val matcher = CLOSING_FENCE.matcher(trySequence)
    if (matcher.find()) matcher.group(1).length else -1
  }
}
