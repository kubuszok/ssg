/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: two java calls Scala.js does not ship — `String.contentEquals(CharSequence)` and
 * `Matcher.useTransparentBounds(boolean)`. Here both exist, so both are java's own call. The generated
 * code reaches this object through the port's call-site substitutions (ssg-md/port/main.conf). */
package ssg
package md
package util
package misc

import java.util.regex.Matcher

object JavaGaps {

  def contentEquals(s: String, cs: CharSequence): Boolean = s.contentEquals(cs)

  def useTransparentBounds(matcher: Matcher, transparent: Boolean): Matcher = matcher.useTransparentBounds(transparent)
}
