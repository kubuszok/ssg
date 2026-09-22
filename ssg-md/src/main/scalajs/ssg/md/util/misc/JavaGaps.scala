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

  /** Java's contract for `StringBuilder.append(CharSequence, int, int)`: the characters `start` until `end`, read one by one — Scala.js reads them through the sequence's `toString`, which a `toString` written over this call recurses into. */
  def append(sb: java.lang.StringBuilder, s: CharSequence, start: Int, end: Int): java.lang.StringBuilder = {
    val cs = if (s == null) "null" else s // java interop boundary: java's own null rule for this call
    if (start < 0 || start > end || end > cs.length) {
      throw new IndexOutOfBoundsException("start " + start + ", end " + end + ", length " + cs.length)
    }
    var i = start
    while (i < end) {
      sb.append(cs.charAt(i))
      i += 1
    }
    sb
  }

  /** Java's contract for `StringBuilder.append(CharSequence)`: the sequence's characters one by one (a `String` copied whole); a `toString` written as `sb.append(this)` recurses through Scala.js's, which reads `toString`. */
  def append(sb: java.lang.StringBuilder, s: CharSequence): java.lang.StringBuilder =
    s match {
      case null      => sb.append("null") // java interop boundary: java's own null rule for this call
      case str: String => sb.append(str)
      case cs          => append(sb, cs, 0, cs.length)
    }

  /** A Scala.js matcher's bounds are always opaque, which is what `false` asks for (flexmark never asks for anything else); transparent bounds are refused rather than ignored. */
  def useTransparentBounds(matcher: Matcher, transparent: Boolean): Matcher = {
    if (transparent) {
      throw new UnsupportedOperationException("Matcher.useTransparentBounds(true) is not available on Scala.js")
    }
    matcher
  }

  def isInstance(cls: Class[?], o: Object): Boolean = cls.isInstance(o)
}
