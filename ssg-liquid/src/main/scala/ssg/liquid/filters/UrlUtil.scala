/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native utility shared by Relative_Url and Absolute_Url.
 */
package ssg
package liquid
package filters

/** Shared URI path utilities for URL filters. */
object UrlUtil {

  /** Collapse runs of consecutive '/' in a URI path to a single '/', mirroring the empty-segment removal that java.net.URI.normalize() performs on the JVM but which Scala Native's normalize() omits.
    * Idempotent: single-slash paths are fixed points, so this is a no-op on JVM/JS where normalize() already collapsed. Operates on raw (un-percent-decoded) path text; '%XX' sequences contain no '/'
    * so they are never touched, and literal '/' separators stay literal.
    */
  def collapseSlashes(path: String): String =
    if (path.indexOf("//") < 0) path
    else {
      val sb        = new StringBuilder(path.length)
      var prevSlash = false
      var i         = 0
      while (i < path.length) {
        val c       = path.charAt(i)
        val isSlash = c == '/'
        if (!(isSlash && prevSlash)) sb.append(c)
        prevSlash = isSlash
        i += 1
      }
      sb.toString
    }
}
