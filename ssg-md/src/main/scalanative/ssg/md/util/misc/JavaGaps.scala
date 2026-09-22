/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: the java calls Scala.js does not ship, and one this platform ships but refuses. The
 * generated code reaches this object through the port's call-site substitutions (ssg-md/port/main.conf). */
package ssg
package md
package util
package misc

import java.util.regex.Matcher

object JavaGaps {

  def contentEquals(s: String, cs: CharSequence): Boolean = s.contentEquals(cs)

  /** Scala Native's `Matcher.useTransparentBounds` throws for any argument (RE2 has no lookaround to see past a bound); a Native matcher's bounds are always opaque, which is what `false` asks for. */
  def useTransparentBounds(matcher: Matcher, transparent: Boolean): Matcher = {
    if (transparent) {
      throw new UnsupportedOperationException("Matcher.useTransparentBounds(true) is not available on Scala Native")
    }
    matcher
  }

  def append(sb: java.lang.StringBuilder, s: CharSequence, start: Int, end: Int): java.lang.StringBuilder = sb.append(s, start, end)

  /** Java's contract for `StringBuilder.append(CharSequence)`: the sequence's characters one by one (a `String` copied whole); a `toString` written as `sb.append(this)` recurses through Scala
    * Native's, which reads `toString`, until the stack is gone.
    */
  def append(sb: java.lang.StringBuilder, s: CharSequence): java.lang.StringBuilder =
    s match {
      case null => sb.append("null") // java interop boundary: java's own null rule for this call
      case str: String => sb.append(str)
      case cs =>
        var i = 0
        while (i < cs.length) {
          sb.append(cs.charAt(i))
          i += 1
        }
        sb
    }

  /** Java's `Class.isInstance(null)` is false; Scala Native reads the object's class first and throws. */
  def isInstance(cls: Class[?], o: Object): Boolean = if (o == null) false else cls.isInstance(o) // java interop boundary: java's own null rule
}
