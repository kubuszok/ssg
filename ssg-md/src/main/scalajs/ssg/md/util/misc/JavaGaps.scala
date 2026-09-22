/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js: two java calls its java library does not ship — `String.contentEquals(CharSequence)` and
 * `Matcher.useTransparentBounds(boolean)`. The generated code reaches this object through the port's
 * call-site substitutions (ssg-md/port/main.conf); the JVM and Scala Native make java's own call. */
package ssg
package md
package util
package misc

import java.util.regex.Matcher

object JavaGaps {

  /** Java's contract: the same length and the same chars, whatever kind of CharSequence the other side is. */
  def contentEquals(s: String, cs: CharSequence): Boolean = {
    val n = s.length
    if (n != cs.length()) {
      false
    } else {
      var i = 0
      while (i < n && s.charAt(i) == cs.charAt(i)) i += 1
      i == n
    }
  }

  /** A Scala.js matcher's bounds are always opaque, which is what `false` asks for (flexmark never asks for anything else); transparent bounds are refused rather than ignored. */
  def useTransparentBounds(matcher: Matcher, transparent: Boolean): Matcher = {
    if (transparent) {
      throw new UnsupportedOperationException("Matcher.useTransparentBounds(true) is not available on Scala.js")
    }
    matcher
  }
}
