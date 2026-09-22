/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js: a list item marker at the start of the sequence, found with flexmark's own pattern, whose
 * lookahead asks what follows the marker. Scala Native's RE2 engine has no lookahead and asks the
 * same question in code at the same name. */
package ssg
package md
package parser
package core

import java.util.regex.Matcher

object ListMarkers {

  /** the matcher positioned on the marker, or null when there is none — java's own `find()` contract */
  def find(parsing: ssg.md.ast.util.Parsing, rest: CharSequence): Matcher = {
    val matcher = parsing.LIST_ITEM_MARKER.matcher(rest)
    if (matcher.find()) matcher else null
  }
}
