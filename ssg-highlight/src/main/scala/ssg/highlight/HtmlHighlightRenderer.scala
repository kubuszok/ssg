/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** Renders highlight captures as HTML `<span>` elements over an escaped source string.
  *
  * Algorithm (span-stack sweep, ISS-1091). Highlight captures coming from a tree-sitter tree query are nested-or-disjoint (syntax-tree nodes nest strictly), but the renderer must also stay correct —
  * and balanced — when captures partially overlap or share a range. The previous implementation sorted by `(startByte, -endByte)` and emitted each span whole (open+text+close); every inner capture
  * then landed in a skip/duplicate branch, dropping nested captures and corrupting HTML on overlap.
  *
  * The rewrite models rendering as a sweep over span boundaries while maintaining an explicit stack of currently-open spans, mirroring tree-sitter-highlight's HighlightStart/Source/HighlightEnd event
  * model:
  *   1. boundaries are the distinct start/end byte offsets (clamped into the source);
  *   2. between consecutive boundaries the source slice is emitted (HTML-escaped) once;
  *   3. at each boundary, spans ending there are closed and spans starting there are opened, keeping the tag stack balanced at all times.
  *
  * Overlap policy (the implementer's choice within the balanced-nesting contract — see `HtmlHighlightRendererOverlapPolicySuite`): **close-and-reopen (split on overlap)**. To close a span that is not
  * on top of the stack, every span above it is first closed, the target span is popped, and the still-open spans above are re-opened (each as a new `<span>` of the same class). Thus a partial overlap
  * `[0,5)a` + `[3,8)b` renders as `<span hl-a>012<span hl-b>34</span></span><span hl-b>567</span>` — every byte appears exactly once, every tag is balanced, and the more-recently-opened span nests
  * inside.
  *
  * Ordering at a boundary: spans starting at the same offset open longest-first (`endByte` descending) so a wider capture wraps a narrower one; identical ranges nest in the order given. Zero-width
  * spans (`startByte == endByte`) are skipped — they would emit an empty `<span></span>` carrying no text. Offsets are clamped to the source length (matching the existing `appendEscaped` robustness);
  * byte-offset slicing is left exactly as before (offset semantics are tracked separately by ISS-1092).
  */
object HtmlHighlightRenderer {

  private val cssPrefix = "hl-"

  def render(source: String, spans: Seq[HighlightSpan]): String = {
    val sourceBytes = source.getBytes("UTF-8")
    val length      = sourceBytes.length
    val sb          = new StringBuilder

    // Clamp offsets into the source and drop zero-width / inverted spans: they carry no
    // text and would only emit empty <span></span> pairs.
    val clamped = spans.flatMap { span =>
      val start = math.max(0, math.min(span.startByte, length))
      val end   = math.max(0, math.min(span.endByte, length))
      if (start < end) Some(HighlightSpan(start, end, span.captureName)) else None
    }

    if (clamped.isEmpty) {
      appendEscaped(sb, sourceBytes, 0, length)
      sb.toString
    } else {
      // Distinct, ascending boundary offsets where the open-span set can change.
      val boundaries = (clamped.flatMap(s => Seq(s.startByte, s.endByte)) :+ length).distinct.sorted

      // Stack of currently-open spans (bottom = outermost). Each entry keeps its endByte so
      // a boundary can decide which spans must close, and its capture class for re-opening.
      val open = scala.collection.mutable.ArrayBuffer.empty[HighlightSpan]
      var pos  = 0

      for (boundary <- boundaries) {
        // 1. Emit the source slice leading up to this boundary, inside whatever spans are open.
        if (pos < boundary) {
          appendEscaped(sb, sourceBytes, pos, boundary)
          pos = boundary
        }

        // 2. Close every span whose endByte == boundary. The stack must stay balanced, so a
        //    span that is not on top cannot be closed directly: pop the top, and if it does
        //    not itself end here, remember it for re-opening. Repeat until no open span ends
        //    at the boundary, then re-open the remembered spans (bottom-to-top order) — this
        //    is the close-and-reopen overlap policy.
        var reopen = List.empty[HighlightSpan]
        while (open.lastIndexWhere(_.endByte == boundary) >= 0) {
          val top = open.remove(open.length - 1)
          closeSpan(sb)
          if (top.endByte != boundary) {
            reopen = top :: reopen
          }
        }
        for (span <- reopen) {
          openSpan(sb, span.captureName)
          open += span
        }

        // 3. Open every span that starts at this boundary, widest-first so larger captures
        //    wrap smaller ones; identical ranges nest in the given order.
        val starting = clamped.filter(_.startByte == boundary).sortBy(s => -s.endByte)
        for (span <- starting) {
          openSpan(sb, span.captureName)
          open += span
        }
      }

      // Any source past the last boundary (defensive — `length` is always a boundary).
      if (pos < length) {
        appendEscaped(sb, sourceBytes, pos, length)
      }

      sb.toString
    }
  }

  private def openSpan(sb: StringBuilder, captureName: String): Unit = {
    sb.append("<span class=\"").append(cssPrefix).append(cssClass(captureName)).append("\">")
    ()
  }

  private def closeSpan(sb: StringBuilder): Unit = {
    sb.append("</span>")
    ()
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
