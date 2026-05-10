/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid treemap diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package treemap

import ssg.mermaid.parse.ParseException

import scala.collection.mutable

/** Hand-written parser for Mermaid treemap syntax.
  *
  * Supported syntax:
  *   - `treemap` — header
  *   - `Label: value` — leaf node with value
  *   - Indentation-based nesting for hierarchical data
  */
object TreemapParser {

  def parse(input: String): TreemapDb = {
    val db      = new TreemapDb
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n")

    var i = 0
    while (i < lines.length && !lines(i).trim.toLowerCase.startsWith("treemap")) i += 1
    if (i >= lines.length) throw new ParseException("Expected 'treemap' keyword", 1, 1)
    i += 1

    val stack = mutable.ArrayBuffer.empty[(Int, TreemapNode)]

    while (i < lines.length) {
      val line = lines(i); i += 1; val trimmed = line.trim
      if (trimmed.isEmpty || trimmed.startsWith("%%")) { /* skip */ }
      else if (trimmed.toLowerCase.startsWith("title")) { db.title = trimmed.substring(5).trim }
      else {
        val indent         = line.length - line.stripLeading().length
        val colonIdx       = trimmed.indexOf(':')
        val (label, value) = if (colonIdx >= 0) {
          val l = trimmed.substring(0, colonIdx).trim
          val v =
            try trimmed.substring(colonIdx + 1).trim.toDouble
            catch { case _: NumberFormatException => 1.0 }
          (l, v)
        } else (trimmed, 1.0)

        val node = TreemapNode(label, value)
        while (stack.nonEmpty && stack.last._1 >= indent) stack.remove(stack.size - 1)

        if (stack.isEmpty) { db.roots += node }
        else { stack.last._2.children += node }
        stack += ((indent, node))
      }
    }
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "")
}
