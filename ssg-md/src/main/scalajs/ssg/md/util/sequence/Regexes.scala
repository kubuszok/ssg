/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js: its regex compiler targets ECMAScript 2015 here, where a Unicode category `\p{Xx}` is
 * refused ("requires ECMAScript 2018") and a case-insensitive group `(?i:…)` is refused at any
 * level; both are restated by `RegexPortability` before the pattern is compiled. Everything else
 * flexmark writes — lookahead, `\Q…\E`, a leading `(?i)` — the compiler accepts. The generated code
 * reaches this object through the port's call-site substitutions (ssg-md/port/main.conf). */
package ssg
package md
package util
package sequence

import java.util.regex.Pattern

object Regexes {

  def compile(regex: String): Pattern = Pattern.compile(portable(regex))

  def compile(regex: String, flags: Int): Pattern = Pattern.compile(portable(regex), flags)

  private def portable(regex: String): String =
    RegexPortability.foldScopedCaseInsensitive(RegexPortability.expandUnicodeCategories(regex))
}
