/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Escape_Once.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaced negative lookahead regex with manual scan
 *               for cross-platform compatibility (Native lacks lookahead)
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Escape_Once.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

/** Escapes HTML without double-escaping existing entities. */
class Escape_Once extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val str = asString(value, context)
    val sb  = new java.lang.StringBuilder(str.length())
    var i   = 0
    while (i < str.length()) {
      val c = str.charAt(i)
      if (c == '&') {
        // Check if this is already an HTML entity: &name; or &#123; or &#x1F;
        val entityEnd = findEntityEnd(str, i)
        if (entityEnd > 0) {
          // Already an entity — keep as-is
          sb.append(str.substring(i, entityEnd))
          i = entityEnd
        } else {
          sb.append("&amp;")
          i += 1
        }
      } else if (c == '<') {
        sb.append("&lt;")
        i += 1
      } else if (c == '>') {
        sb.append("&gt;")
        i += 1
      } else if (c == '"') {
        sb.append("&quot;")
        i += 1
      } else {
        sb.append(c)
        i += 1
      }
    }
    sb.toString()
  }

  /** Returns the end index of an HTML entity starting at pos, or -1 if not an entity. */
  private def findEntityEnd(str: String, pos: Int): Int =
    // Must start with &
    if (pos >= str.length() || str.charAt(pos) != '&') {
      -1
    } else {
      var i = pos + 1
      if (i >= str.length()) {
        -1
      } else if (str.charAt(i) == '#') {
        // Numeric entity: &#123; or &#x1F;
        i += 1
        if (i >= str.length()) {
          -1
        } else if (str.charAt(i) == 'x' || str.charAt(i) == 'X') {
          // Hex: &#xAF;
          i += 1
          val start = i
          while (i < str.length() && isHexDigit(str.charAt(i))) i += 1
          if (i > start && i < str.length() && str.charAt(i) == ';') {
            i + 1
          } else {
            -1
          }
        } else {
          // Decimal: &#123;
          val start = i
          while (i < str.length() && str.charAt(i) >= '0' && str.charAt(i) <= '9') i += 1
          if (i > start && i < str.length() && str.charAt(i) == ';') {
            i + 1
          } else {
            -1
          }
        }
      } else {
        // Named entity: &amp;
        val start = i
        while (i < str.length() && isLetter(str.charAt(i))) i += 1
        if (i > start && i < str.length() && str.charAt(i) == ';') {
          i + 1
        } else {
          -1
        }
      }
    }

  private def isLetter(c:   Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
  private def isHexDigit(c: Char): Boolean = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
}
