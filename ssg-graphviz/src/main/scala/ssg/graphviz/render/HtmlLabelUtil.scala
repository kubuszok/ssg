/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Utility for stripping HTML-like label tags from Graphviz DOT labels.
 *
 * Graphviz HTML-like labels (delimited by `<...>` in DOT source) contain
 * HTML-subset tags (<B>, <I>, <FONT>, <BR/>, <TABLE>, <TR>, <TD>) that
 * control styling. The rendered output shows only the TEXT CONTENT of the
 * markup, with tags stripped.
 *
 * This utility extracts the visible text content from such labels. It is
 * used by both GraphBuilder (for width estimation) and DotRenderer (for
 * rendering), ensuring they agree on what text is visible.
 */
package ssg
package graphviz
package render

import scala.util.boundary
import scala.util.boundary.break

object HtmlLabelUtil {

  /** Strips HTML-like tags from a Graphviz HTML label, returning only the visible text content.
    *
    * For example:
    *   - `<B>bold</B>` -> `bold`
    *   - `<FONT COLOR="red"><B>styled</B> text</FONT>` -> `styled text`
    *   - `<TABLE><TR><TD>cell1</TD><TD>cell2</TD></TR></TABLE>` -> `cell1cell2`
    *   - `plain text` (no tags) -> `plain text` (unchanged)
    */
  def stripHtmlTags(text: String): String = boundary {
    if (!text.contains('<')) { break(text) }
    val sb    = new StringBuilder
    var inTag = false
    text.foreach { ch =>
      if (ch == '<') { inTag = true }
      else if (ch == '>') { inTag = false }
      else if (!inTag) { sb.append(ch) }
    }
    sb.toString
  }
}
