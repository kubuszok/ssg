/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform regex utilities. Scala Native's re2 engine does not support
 * \Q...\E literal quoting. These helpers manually escape regex metacharacters
 * so patterns compile identically on JVM, Scala.js, and Scala Native.
 */
package ssg
package md
package util
package sequence

/** Cross-platform replacements for `\Q...\E` regex literal quoting.
  *
  * The JVM's `java.util.regex` and `Pattern.quote()` use `\Q...\E` internally,
  * which is not supported by Scala Native's re2-based regex engine. These methods
  * manually escape regex metacharacters instead.
  */
object RegexCompat {

  /** Regex metacharacters that must be escaped with a backslash. */
  private val REGEX_META: String = "\\^$.|?*+()[]{}/"

  /** Escape all regex metacharacters in `s` so it matches literally.
    *
    * This is a cross-platform replacement for `Pattern.quote(s)` /
    * `"\\Q" + s + "\\E"`.
    */
  def regexEscape(s: String): String = {
    val sb = new StringBuilder(s.length + 8)
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (REGEX_META.indexOf(c) >= 0) {
        sb.append('\\')
      }
      sb.append(c)
      i += 1
    }
    sb.toString
  }

  /** Escape characters for use inside a regex character class `[...]`.
    *
    * Inside a character class, only `]`, `\`, `^` (at start), and `-` (between
    * chars) are special. This method escapes all four unconditionally.
    */
  def charClassEscape(chars: String): String = {
    val sb = new StringBuilder(chars.length + 4)
    var i = 0
    while (i < chars.length) {
      val c = chars.charAt(i)
      c match {
        case ']' | '\\' | '^' | '-' =>
          sb.append('\\')
          sb.append(c)
        case _ =>
          sb.append(c)
      }
      i += 1
    }
    sb.toString
  }
}
