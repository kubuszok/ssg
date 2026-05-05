/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

object HtmlHighlightRenderer {

  private val cssPrefix = "hl-"

  def render(source: String, spans: Seq[HighlightSpan]): String = {
    val sourceBytes = source.getBytes("UTF-8")
    val sorted      = spans.sortBy(s => (s.startByte, -s.endByte))
    val sb          = new StringBuilder
    var pos         = 0

    for (span <- sorted)
      if (span.startByte < pos) {
        if (span.endByte > pos) {
          sb.append("</span>")
          appendEscaped(sb, sourceBytes, pos, span.endByte)
          sb.append("<span class=\"").append(cssPrefix).append(cssClass(span.captureName)).append("\">")
        }
      } else {
        if (pos < span.startByte) {
          appendEscaped(sb, sourceBytes, pos, span.startByte)
        }
        sb.append("<span class=\"").append(cssPrefix).append(cssClass(span.captureName)).append("\">")
        appendEscaped(sb, sourceBytes, span.startByte, span.endByte)
        sb.append("</span>")
        pos = span.endByte
      }

    if (pos < sourceBytes.length) {
      appendEscaped(sb, sourceBytes, pos, sourceBytes.length)
    }

    sb.toString
  }

  private def cssClass(captureName: String): String =
    captureName.replace('.', '-')

  private def appendEscaped(sb: StringBuilder, bytes: Array[Byte], from: Int, to: Int): Unit = {
    val text = new String(bytes, from, math.min(to, bytes.length) - from, "UTF-8")
    var i    = 0
    while (i < text.length) {
      text.charAt(i) match {
        case '&'  => sb.append("&amp;")
        case '<'  => sb.append("&lt;")
        case '>'  => sb.append("&gt;")
        case '"'  => sb.append("&quot;")
        case '\'' => sb.append("&#39;")
        case c    => sb.append(c)
      }
      i += 1
    }
  }
}
