/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Strip_HTML.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Manual string processing instead of regex with (?s) DOTALL
 *               for cross-platform compatibility (Native lacks (?s) support)
 */
package ssg
package liquid
package filters

class Strip_HTML extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    var html = asString(value, context)
    // Remove block-level elements: <script>...</script>, <style>...</style>, <!--...-->
    html = Strip_HTML.removeBlocks(html, "<script", "</script>")
    html = Strip_HTML.removeBlocks(html, "<style", "</style>")
    html = Strip_HTML.removeComments(html)
    // Remove remaining HTML tags: <...>
    html = Strip_HTML.removeTags(html)
    html
  }
}

object Strip_HTML {

  /** Removes blocks like <script...>...</script> (case-insensitive open tag). */
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
        if (closeIdx < 0) {
          // No closing tag — remove rest
          i = html.length()
        } else {
          i = closeIdx + closeTag.length()
        }
      }
    }
    sb.toString
  }

  /** Removes HTML comments <!-- ... --> */
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
        if (closeIdx < 0) {
          i = html.length()
        } else {
          i = closeIdx + 3
        }
      }
    }
    sb.toString
  }

  /** Removes all HTML tags <...> */
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
        if (closeIdx < 0) {
          // No closing > — keep the rest as-is (not a tag)
          sb.append(html, openIdx, html.length())
          i = html.length()
        } else {
          i = closeIdx + 1
        }
      }
    }
    sb.toString
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
