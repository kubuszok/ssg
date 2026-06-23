/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Strip_HTML.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Manual string processing replicating Pattern.MULTILINE (NOT
 *               DOTALL/(?s)) — the original liqp compiles its regexes with
 *               Pattern.MULTILINE which does NOT make . cross line terminators,
 *               so .*? stays within one line. Cross-platform (Native lacks
 *               regex (?s) support).
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Strip_HTML.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

class Strip_HTML extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView = {
    var html = asString(value, context)
    // Remove block-level elements: <script>...</script>, <style>...</style>, <!--...-->
    html = Strip_HTML.removeBlocks(html, "<script", "</script>")
    html = Strip_HTML.removeBlocks(html, "<style", "</style>")
    html = Strip_HTML.removeComments(html)
    // Remove remaining HTML tags: <...>
    html = Strip_HTML.removeTags(html)
    DataView.from(html)
  }
}

object Strip_HTML {

  /** Removes blocks like <script...>...</script> (case-insensitive open tag).
    *
    * Replicates liqp's `<script.*?</script>` compiled with Pattern.MULTILINE (Strip_HTML.java:14): `.*?` does NOT cross line terminators, so only single-line blocks are removed. A multi-line block is
    * left for the subsequent TAGS pass.
    */
  private[filters] def removeBlocks(html: String, openTag: String, closeTag: String): String = {
    val sb = new java.lang.StringBuilder(html.length())
    var i  = 0
    while (i < html.length()) {
      val openIdx = indexOfIgnoreCase(html, openTag, i)
      if (openIdx < 0) {
        sb.append(html, i, html.length())
        i = html.length()
      } else {
        sb.append(html, i, openIdx)
        val closeIdx = indexOfIgnoreCase(html, closeTag, openIdx + openTag.length())
        if (closeIdx < 0 || containsNewline(html, openIdx + openTag.length(), closeIdx)) {
          // No closing tag on the same line — not a MULTILINE match.
          // Append the first char of the open-tag marker and resume scanning,
          // replicating the regex engine advancing one position on mismatch.
          sb.append(html.charAt(openIdx))
          i = openIdx + 1
        } else {
          i = closeIdx + closeTag.length()
        }
      }
    }
    sb.toString
  }

  /** Removes HTML comments `<!-- ... -->`.
    *
    * Replicates liqp's `<!--.*?-->` compiled with Pattern.MULTILINE (Strip_HTML.java:14): `.*?` does NOT cross line terminators, so only single-line comments are removed.
    */
  private[filters] def removeComments(html: String): String = {
    val sb = new java.lang.StringBuilder(html.length())
    var i  = 0
    while (i < html.length()) {
      val openIdx = html.indexOf("<!--", i)
      if (openIdx < 0) {
        sb.append(html, i, html.length())
        i = html.length()
      } else {
        sb.append(html, i, openIdx)
        val closeIdx = html.indexOf("-->", openIdx + 4)
        if (closeIdx < 0 || containsNewline(html, openIdx + 4, closeIdx)) {
          // No closing --> on the same line — not a MULTILINE match.
          sb.append(html.charAt(openIdx))
          i = openIdx + 1
        } else {
          i = closeIdx + 3
        }
      }
    }
    sb.toString
  }

  /** Removes all HTML tags `<...>`.
    *
    * Replicates liqp's `<.*?>` compiled with Pattern.MULTILINE (Strip_HTML.java:17): `.*?` does NOT cross line terminators, so only tags whose `<` and `>` are on the same line are removed.
    */
  private[filters] def removeTags(html: String): String = {
    val sb = new java.lang.StringBuilder(html.length())
    var i  = 0
    while (i < html.length()) {
      val openIdx = html.indexOf('<', i)
      if (openIdx < 0) {
        sb.append(html, i, html.length())
        i = html.length()
      } else {
        sb.append(html, i, openIdx)
        val closeIdx = html.indexOf('>', openIdx + 1)
        if (closeIdx < 0 || containsNewline(html, openIdx + 1, closeIdx)) {
          // No closing > on the same line — not a MULTILINE match.
          sb.append(html.charAt(openIdx))
          i = openIdx + 1
        } else {
          i = closeIdx + 1
        }
      }
    }
    sb.toString
  }

  /** Returns true if `str` contains a newline character in `[from, until)`. */
  private def containsNewline(str: String, from: Int, until: Int): Boolean = {
    var j     = from
    var found = false
    while (j < until && !found) {
      if (str.charAt(j) == '\n') {
        found = true
      }
      j += 1
    }
    found
  }

  private def indexOfIgnoreCase(str: String, target: String, fromIndex: Int): Int = {
    val targetLower = target.toLowerCase()
    val limit       = str.length() - target.length()
    var i           = fromIndex
    var result      = -1
    while (i <= limit && result < 0) {
      if (str.substring(i, i + target.length()).toLowerCase() == targetLower) {
        result = i
      }
      i += 1
    }
    result
  }
}
